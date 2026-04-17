from __future__ import annotations

import argparse
import copy
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import resolve_common_dt_partition_date, write_partition
from utils.spark_factory import build_spark_session


DEFAULT_DWD_CONFIG: dict[str, Any] = {
    "source_tables": {
        "users": "ods.ods_pg_users_full",
        "movies": "ods.ods_pg_movies_full",
        "comments": "ods.ods_pg_comments_full",
        "favorite_folders": "ods.ods_pg_favorite_folders_full",
        "ratings": "ods.ods_pg_ratings_full",
        "comment_likes": "ods.ods_pg_comment_likes_full",
        "favorites": "ods.ods_pg_favorites_full",
        "view_history": "ods.ods_pg_view_history_full",
        "watched_movies": "ods.ods_pg_watched_movies_full",
    },
    "target_table": "dwd.dwd_user_event_wide_di",
    "sink_path": "hdfs:///warehouse/movie/dwd/user_event_wide_di",
}

EVENT_SCHEMA: dict[str, str] = {
    "topic": "string",
    "event_key": "string",
    "event_id": "string",
    "event_type": "string",
    "occurred_at": "timestamp",
    "ingest_time": "timestamp",
    "hh": "string",
    "user_id": "string",
    "movie_id": "bigint",
    "comment_id": "bigint",
    "folder_id": "bigint",
    "folder_name": "string",
    "folder_is_public": "tinyint",
    "operation": "string",
    "rating": "int",
    "search_keyword": "string",
    "result_count": "bigint",
    "filter_conditions": "string",
    "search_time": "bigint",
    "event_data": "string",
    "raw_json": "string",
    "session_id": "string",
    "page_url": "string",
    "sequence_number": "int",
    "client_timestamp": "bigint",
    "entry_url": "string",
    "first_referrer": "string",
    "user_agent": "string",
    "device_type": "string",
    "session_start_time": "bigint",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build dwd_user_event_wide_di from PostgreSQL ODS snapshots in T+1 batch mode."
    )
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument(
        "--snapshot-date",
        default="",
        help="Snapshot partition date for PostgreSQL ODS full tables. "
        "If omitted, use the latest common dt partition not newer than calc-date.",
    )
    return parser.parse_args()


def merge_nested_dict(defaults: dict[str, Any], overrides: dict[str, Any]) -> dict[str, Any]:
    merged = copy.deepcopy(defaults)
    for key, value in overrides.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = merge_nested_dict(merged[key], value)
        else:
            merged[key] = value
    return merged


def load_partition(spark, table_name: str, partition_date: str) -> DataFrame:
    return spark.table(table_name).where(F.col("dt") == partition_date)


def filter_by_calc_date(df: DataFrame, ts_col: str, calc_date: str) -> DataFrame:
    return df.where(F.to_date(F.col(ts_col)) == F.lit(calc_date).cast("date"))


def json_payload(**fields: F.Column) -> F.Column:
    return F.to_json(F.struct(*(column.alias(name) for name, column in fields.items())))


def build_hash_key(*columns: F.Column) -> F.Column:
    return F.sha2(F.concat_ws("||", *(F.coalesce(column.cast("string"), F.lit("")) for column in columns)), 256)


def align_event_schema(df: DataFrame) -> DataFrame:
    aligned = df
    for column_name, column_type in EVENT_SCHEMA.items():
        if column_name in aligned.columns:
            aligned = aligned.withColumn(column_name, F.col(column_name).cast(column_type))
        else:
            aligned = aligned.withColumn(column_name, F.lit(None).cast(column_type))
    return aligned.select(*EVENT_SCHEMA.keys())


def union_all(frames: list[DataFrame]) -> DataFrame:
    if not frames:
        raise ValueError("frames must not be empty")

    result = frames[0]
    for frame in frames[1:]:
        result = result.unionByName(frame)
    return result


def build_view_history_events(view_history_df: DataFrame, movies_df: DataFrame, calc_date: str) -> DataFrame:
    # Filter view history whose movie_id exists in movies table
    valid_movie_ids = movies_df.select("movie_id")
    filtered_df = filter_by_calc_date(
        view_history_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull() & F.col("view_time").isNotNull())
        .join(valid_movie_ids, "movie_id", "inner"),
        "view_time",
        calc_date,
    )

    payload = json_payload(
        userId=F.col("user_id").cast("string"),
        movieId=F.col("movie_id").cast("bigint"),
        historyId=F.col("history_id").cast("bigint"),
        source=F.lit("postgres_t_plus_1"),
    )
    event_key = F.coalesce(
        F.col("history_id").cast("string"),
        build_hash_key(F.col("user_id"), F.col("movie_id"), F.col("view_time")),
    )

    return align_event_schema(
        filtered_df.select(
            F.lit("postgres_view_history").alias("topic"),
            event_key.alias("event_key"),
            F.concat(F.lit("pg_view_"), event_key).alias("event_id"),
            F.lit("view_history").alias("event_type"),
            F.col("view_time").alias("occurred_at"),
            F.col("view_time").alias("ingest_time"),
            F.date_format(F.col("view_time"), "HH").alias("hh"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            payload.alias("event_data"),
            payload.alias("raw_json"),
        )
    )


def build_rating_events(ratings_df: DataFrame, calc_date: str) -> DataFrame:
    filtered_df = filter_by_calc_date(
        ratings_df.where(
            F.col("user_id").isNotNull()
            & F.col("movie_id").isNotNull()
            & F.col("rating").isNotNull()
            & F.col("rating_time").isNotNull()
        ),
        "rating_time",
        calc_date,
    )

    event_key = build_hash_key(F.col("user_id"), F.col("movie_id"), F.col("rating_time"), F.col("rating"))
    payload = json_payload(
        userId=F.col("user_id").cast("string"),
        movieId=F.col("movie_id").cast("bigint"),
        rating=F.col("rating").cast("int"),
        source=F.lit("postgres_t_plus_1"),
    )

    return align_event_schema(
        filtered_df.select(
            F.lit("postgres_ratings").alias("topic"),
            event_key.alias("event_key"),
            F.concat(F.lit("pg_rating_"), event_key).alias("event_id"),
            F.lit("rating").alias("event_type"),
            F.col("rating_time").alias("occurred_at"),
            F.col("rating_time").alias("ingest_time"),
            F.date_format(F.col("rating_time"), "HH").alias("hh"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("rating").cast("int").alias("rating"),
            payload.alias("event_data"),
            payload.alias("raw_json"),
        )
    )


def build_comment_events(comments_df: DataFrame, movies_df: DataFrame, calc_date: str) -> DataFrame:
    # Filter comments whose movie_id exists in movies table
    valid_movie_ids = movies_df.select("movie_id")
    filtered_df = filter_by_calc_date(
        comments_df.where(
            F.col("comment_id").isNotNull()
            & F.col("user_id").isNotNull()
            & F.col("movie_id").isNotNull()
            & F.col("comment_time").isNotNull()
        )
        .join(valid_movie_ids, "movie_id", "inner"),
        "comment_time",
        calc_date,
    )

    payload = json_payload(
        userId=F.col("user_id").cast("string"),
        movieId=F.col("movie_id").cast("bigint"),
        commentId=F.col("comment_id").cast("bigint"),
        commentType=F.col("type").cast("tinyint"),
        source=F.lit("postgres_t_plus_1"),
    )
    event_key = F.col("comment_id").cast("string")

    return align_event_schema(
        filtered_df.select(
            F.lit("postgres_comments").alias("topic"),
            event_key.alias("event_key"),
            F.concat(F.lit("pg_comment_"), event_key).alias("event_id"),
            F.lit("comment").alias("event_type"),
            F.col("comment_time").alias("occurred_at"),
            F.col("comment_time").alias("ingest_time"),
            F.date_format(F.col("comment_time"), "HH").alias("hh"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("comment_id").cast("bigint").alias("comment_id"),
            payload.alias("event_data"),
            payload.alias("raw_json"),
        )
    )


def build_comment_like_events(comment_likes_df: DataFrame, comments_df: DataFrame, calc_date: str) -> DataFrame:
    filtered_df = filter_by_calc_date(
        comment_likes_df.where(
            F.col("id").isNotNull()
            & F.col("comment_id").isNotNull()
            & F.col("user_id").isNotNull()
            & F.col("create_time").isNotNull()
        ),
        "create_time",
        calc_date,
    )

    joined_df = filtered_df.alias("cl").join(
        comments_df.alias("c"),
        F.col("cl.comment_id") == F.col("c.comment_id"),
        "left",
    )
    payload = json_payload(
        userId=F.col("cl.user_id").cast("string"),
        movieId=F.col("c.movie_id").cast("bigint"),
        commentId=F.col("cl.comment_id").cast("bigint"),
        source=F.lit("postgres_t_plus_1"),
    )
    event_key = F.col("cl.id").cast("string")

    return align_event_schema(
        joined_df.select(
            F.lit("postgres_comment_likes").alias("topic"),
            event_key.alias("event_key"),
            F.concat(F.lit("pg_comment_like_"), event_key).alias("event_id"),
            F.lit("comment_like").alias("event_type"),
            F.col("cl.create_time").alias("occurred_at"),
            F.col("cl.create_time").alias("ingest_time"),
            F.date_format(F.col("cl.create_time"), "HH").alias("hh"),
            F.col("cl.user_id").cast("string").alias("user_id"),
            F.col("c.movie_id").cast("bigint").alias("movie_id"),
            F.col("cl.comment_id").cast("bigint").alias("comment_id"),
            payload.alias("event_data"),
            payload.alias("raw_json"),
        )
    )


def build_favorite_events(favorites_df: DataFrame, movies_df: DataFrame, calc_date: str) -> DataFrame:
    # Filter favorites whose movie_id exists in movies table
    valid_movie_ids = movies_df.select("movie_id")
    filtered_df = filter_by_calc_date(
        favorites_df.where(
            F.col("user_id").isNotNull()
            & F.col("movie_id").isNotNull()
            & F.col("folder_id").isNotNull()
            & F.col("create_time").isNotNull()
        )
        .join(valid_movie_ids, "movie_id", "inner"),
        "create_time",
        calc_date,
    )

    event_key = build_hash_key(F.col("user_id"), F.col("movie_id"), F.col("folder_id"), F.col("create_time"))
    payload = json_payload(
        userId=F.col("user_id").cast("string"),
        movieId=F.col("movie_id").cast("bigint"),
        folderId=F.col("folder_id").cast("bigint"),
        operation=F.lit("ADD"),
        source=F.lit("postgres_t_plus_1"),
    )

    return align_event_schema(
        filtered_df.select(
            F.lit("postgres_favorites").alias("topic"),
            event_key.alias("event_key"),
            F.concat(F.lit("pg_favorite_"), event_key).alias("event_id"),
            F.lit("favorite").alias("event_type"),
            F.col("create_time").alias("occurred_at"),
            F.col("create_time").alias("ingest_time"),
            F.date_format(F.col("create_time"), "HH").alias("hh"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("folder_id").cast("bigint").alias("folder_id"),
            F.lit("ADD").alias("operation"),
            payload.alias("event_data"),
            payload.alias("raw_json"),
        )
    )


def build_watched_events(watched_movies_df: DataFrame, movies_df: DataFrame, calc_date: str) -> DataFrame:
    # Filter watched movies whose movie_id exists in movies table
    valid_movie_ids = movies_df.select("movie_id")
    filtered_df = filter_by_calc_date(
        watched_movies_df.where(
            F.col("user_id").isNotNull() & F.col("movie_id").isNotNull() & F.col("create_time").isNotNull()
        )
        .join(valid_movie_ids, "movie_id", "inner"),
        "create_time",
        calc_date,
    )

    event_key = build_hash_key(F.col("user_id"), F.col("movie_id"), F.col("create_time"))
    payload = json_payload(
        userId=F.col("user_id").cast("string"),
        movieId=F.col("movie_id").cast("bigint"),
        source=F.lit("postgres_t_plus_1"),
    )

    return align_event_schema(
        filtered_df.select(
            F.lit("postgres_watched_movies").alias("topic"),
            event_key.alias("event_key"),
            F.concat(F.lit("pg_watched_"), event_key).alias("event_id"),
            F.lit("watched").alias("event_type"),
            F.col("create_time").alias("occurred_at"),
            F.col("create_time").alias("ingest_time"),
            F.date_format(F.col("create_time"), "HH").alias("hh"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            payload.alias("event_data"),
            payload.alias("raw_json"),
        )
    )


def build_register_events(users_df: DataFrame, calc_date: str) -> DataFrame:
    filtered_df = filter_by_calc_date(
        users_df.where(F.col("user_id").isNotNull() & F.col("create_time").isNotNull()),
        "create_time",
        calc_date,
    )

    payload = json_payload(
        userId=F.col("user_id").cast("string"),
        email=F.col("email").cast("string"),
        source=F.lit("postgres_t_plus_1"),
    )
    event_key = F.col("user_id").cast("string")

    return align_event_schema(
        filtered_df.select(
            F.lit("postgres_users").alias("topic"),
            event_key.alias("event_key"),
            F.concat(F.lit("pg_user_register_"), event_key).alias("event_id"),
            F.lit("user_register").alias("event_type"),
            F.col("create_time").alias("occurred_at"),
            F.col("create_time").alias("ingest_time"),
            F.date_format(F.col("create_time"), "HH").alias("hh"),
            F.col("user_id").cast("string").alias("user_id"),
            payload.alias("event_data"),
            payload.alias("raw_json"),
        )
    )


def build_favorite_folder_action_events(folders_df: DataFrame, calc_date: str) -> DataFrame:
    base_df = folders_df.where(F.col("id").isNotNull() & F.col("user_id").isNotNull())

    create_df = filter_by_calc_date(base_df.where(F.col("create_time").isNotNull()), "create_time", calc_date).select(
        F.col("id").cast("bigint").alias("folder_id"),
        F.col("user_id").cast("string").alias("user_id"),
        F.col("name").alias("folder_name"),
        F.col("is_public").cast("tinyint").alias("folder_is_public"),
        F.col("create_time").alias("event_time"),
        F.lit("CREATE").alias("operation"),
    )

    update_df = filter_by_calc_date(base_df.where(F.col("update_time").isNotNull()), "update_time", calc_date).where(
        F.col("create_time").isNull() | (F.col("update_time") != F.col("create_time"))
    ).select(
        F.col("id").cast("bigint").alias("folder_id"),
        F.col("user_id").cast("string").alias("user_id"),
        F.col("name").alias("folder_name"),
        F.col("is_public").cast("tinyint").alias("folder_is_public"),
        F.col("update_time").alias("event_time"),
        F.lit("UPDATE").alias("operation"),
    )

    folder_events_df = union_all([create_df, update_df])
    event_key = build_hash_key(
        F.col("user_id"),
        F.col("folder_id"),
        F.col("operation"),
        F.col("event_time"),
        F.col("folder_name"),
    )
    payload = json_payload(
        userId=F.col("user_id").cast("string"),
        folderId=F.col("folder_id").cast("bigint"),
        folderName=F.col("folder_name").cast("string"),
        isPublic=F.col("folder_is_public").cast("tinyint"),
        operation=F.col("operation").cast("string"),
        source=F.lit("postgres_t_plus_1"),
    )

    return align_event_schema(
        folder_events_df.select(
            F.lit("postgres_favorite_folders").alias("topic"),
            event_key.alias("event_key"),
            F.concat(F.lit("pg_folder_action_"), event_key).alias("event_id"),
            F.lit("favorite_folder_action").alias("event_type"),
            F.col("event_time").alias("occurred_at"),
            F.col("event_time").alias("ingest_time"),
            F.date_format(F.col("event_time"), "HH").alias("hh"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("folder_id").cast("bigint").alias("folder_id"),
            F.col("folder_name").cast("string").alias("folder_name"),
            F.col("folder_is_public").cast("tinyint").alias("folder_is_public"),
            F.col("operation").cast("string").alias("operation"),
            payload.alias("event_data"),
            payload.alias("raw_json"),
        )
    )


def build_postgres_events(
    calc_date: str,
    users_df: DataFrame,
    comments_df: DataFrame,
    folders_df: DataFrame,
    ratings_df: DataFrame,
    comment_likes_df: DataFrame,
    favorites_df: DataFrame,
    view_history_df: DataFrame,
    watched_movies_df: DataFrame,
    movies_df: DataFrame,
) -> DataFrame:
    event_frames = [
        build_view_history_events(view_history_df, movies_df, calc_date),
        build_rating_events(ratings_df, calc_date),
        build_comment_events(comments_df, movies_df, calc_date),
        build_comment_like_events(comment_likes_df, comments_df, calc_date),
        build_favorite_events(favorites_df, movies_df, calc_date),
        build_watched_events(watched_movies_df, movies_df, calc_date),
        build_register_events(users_df, calc_date),
        build_favorite_folder_action_events(folders_df, calc_date),
    ]
    return union_all(event_frames)


def build_wide_table(
    events_df: DataFrame,
    users_df: DataFrame,
    movies_df: DataFrame,
    comments_df: DataFrame,
    folders_df: DataFrame,
    ratings_df: DataFrame,
) -> DataFrame:
    e = events_df.alias("e")
    u = users_df.alias("u")
    c = comments_df.alias("c")
    f = folders_df.alias("f")

    joined_with_comment_df = e.join(c, F.col("e.comment_id") == F.col("c.comment_id"), "left")
    effective_movie_id = F.coalesce(F.col("e.movie_id"), F.col("c.movie_id"))

    joined = (
        joined_with_comment_df.join(u, F.col("e.user_id") == F.col("u.user_id"), "left")
        .join(movies_df.alias("m"), effective_movie_id == F.col("m.movie_id"), "left")
        .join(f, F.col("e.folder_id") == F.col("f.id"), "left")
        .join(
            ratings_df.alias("r"),
            (F.col("e.user_id") == F.col("r.user_id")) & (effective_movie_id == F.col("r.movie_id")),
            "left",
        )
    )

    event_ts = F.coalesce(F.col("e.occurred_at"), F.col("e.ingest_time"))
    event_type = F.col("e.event_type")
    event_operation = F.col("e.operation")

    return joined.select(
        F.col("e.topic").alias("topic"),
        F.col("e.event_key").alias("event_key"),
        F.col("e.event_id").alias("event_id"),
        event_type.alias("event_type"),
        event_ts.alias("event_ts"),
        F.col("e.occurred_at").alias("occurred_at"),
        F.col("e.ingest_time").alias("ingest_time"),
        F.col("e.hh").alias("hh"),
        F.col("e.user_id").alias("user_id"),
        F.col("u.user_nickname").alias("user_nickname"),
        F.col("u.role").cast("tinyint").alias("user_role"),
        F.col("u.status").cast("int").alias("user_status"),
        effective_movie_id.cast("bigint").alias("movie_id"),
        F.col("m.name").alias("movie_name"),
        F.col("m.year").cast("int").alias("movie_year"),
        F.col("m.genres").alias("movie_genres"),
        F.col("m.score").cast("decimal(3,1)").alias("movie_score"),
        F.col("m.douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
        F.col("e.comment_id").alias("comment_id"),
        F.col("c.type").cast("tinyint").alias("comment_type"),
        F.col("c.votes").cast("int").alias("comment_votes"),
        F.col("c.comment_time").alias("comment_time"),
        F.col("c.title").alias("comment_title"),
        F.length(F.col("c.content")).cast("int").alias("comment_content_length"),
        F.col("e.folder_id").alias("folder_id"),
        F.coalesce(F.col("e.folder_name"), F.col("f.name")).alias("folder_name"),
        F.coalesce(F.col("e.folder_is_public"), F.col("f.is_public")).cast("tinyint").alias("folder_is_public"),
        event_operation.alias("operation"),
        F.upper(F.trim(event_operation)).alias("operation_norm"),
        F.col("e.rating").cast("int").alias("rating"),
        F.col("r.rating").cast("int").alias("rating_snapshot"),
        F.col("r.rating_time").alias("rating_time"),
        F.col("e.search_keyword").alias("search_keyword"),
        F.col("e.result_count").cast("bigint").alias("result_count"),
        F.col("e.filter_conditions").alias("filter_conditions"),
        F.col("e.search_time").cast("bigint").alias("search_time"),
        F.when(event_type == F.lit("view_history"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_view"),
        F.when(event_type == F.lit("rating"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_rating"),
        F.when(event_type == F.lit("comment"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_comment"),
        F.when(event_type == F.lit("comment_like"), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_comment_like"),
        F.when(event_type == F.lit("favorite"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_favorite"),
        F.when(event_type == F.lit("watched"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_watched"),
        F.when(event_type == F.lit("search"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_search"),
        F.when(event_type == F.lit("user_register"), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_register"),
        F.when(event_type == F.lit("user_login"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_login"),
        F.when(event_type == F.lit("favorite_folder_action"), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_favorite_folder_action"),
        F.col("e.event_data").alias("event_data"),
        F.col("e.raw_json").alias("raw_json"),
        F.col("e.session_id").alias("session_id"),
        F.col("e.page_url").alias("page_url"),
        F.col("e.sequence_number").alias("sequence_number"),
        F.col("e.client_timestamp").alias("client_timestamp"),
        F.col("e.entry_url").alias("entry_url"),
        F.col("e.first_referrer").alias("referrer"),
        F.col("e.user_agent").alias("user_agent"),
        F.col("e.device_type").alias("device_type"),
        F.col("e.session_start_time").alias("session_start_time"),
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    dwd_config = merge_nested_dict(DEFAULT_DWD_CONFIG, config.get("dwd", {}))

    calc_date = args.calc_date
    requested_snapshot_date = args.snapshot_date.strip()

    spark = build_spark_session("movie-dwd-user-event-wide-di", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS dwd")

        source_tables = dwd_config["source_tables"]
        snapshot_date = resolve_common_dt_partition_date(
            [
                source_tables["users"],
                source_tables["movies"],
                source_tables["comments"],
                source_tables["favorite_folders"],
                source_tables["ratings"],
                source_tables["comment_likes"],
                source_tables["favorites"],
                source_tables["view_history"],
                source_tables["watched_movies"],
            ],
            requested_snapshot_date,
            spark,
            fallback_max_date=calc_date,
        )

        users_df = load_partition(spark, source_tables["users"], snapshot_date)
        movies_df = load_partition(spark, source_tables["movies"], snapshot_date)
        comments_df = load_partition(spark, source_tables["comments"], snapshot_date)
        folders_df = load_partition(spark, source_tables["favorite_folders"], snapshot_date)
        ratings_df = load_partition(spark, source_tables["ratings"], snapshot_date)
        comment_likes_df = load_partition(spark, source_tables["comment_likes"], snapshot_date)
        favorites_df = load_partition(spark, source_tables["favorites"], snapshot_date)
        view_history_df = load_partition(spark, source_tables["view_history"], snapshot_date)
        watched_movies_df = load_partition(spark, source_tables["watched_movies"], snapshot_date)

        events_df = build_postgres_events(
            calc_date=calc_date,
            users_df=users_df,
            comments_df=comments_df,
            folders_df=folders_df,
            ratings_df=ratings_df,
            comment_likes_df=comment_likes_df,
            favorites_df=favorites_df,
            view_history_df=view_history_df,
            watched_movies_df=watched_movies_df,
            movies_df=movies_df,
        )
        wide_df = build_wide_table(events_df, users_df, movies_df, comments_df, folders_df, ratings_df)

        target_table = dwd_config["target_table"]
        sink_path = dwd_config["sink_path"]
        write_partition(wide_df, target_table, sink_path, calc_date, spark)

        print(
            "DWD build finished in PostgreSQL T+1 batch mode. "
            f"table={target_table}, dt={calc_date}, source_snapshot_dt={snapshot_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
