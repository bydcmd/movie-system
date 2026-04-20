from __future__ import annotations

import argparse
import copy
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401
from postgres_jdbc import build_jdbc_reader

from utils.config_loader import load_config
from utils.hive_utils import resolve_common_dt_partition_date, write_partition
from utils.spark_factory import build_spark_session


DEFAULT_DW_EVENT_FACT_CONFIG: dict[str, Any] = {
    "source_mode": "jdbc",
    "source_tables": {
        "users": "public.users",
        "movies": "public.movies",
        "comments": "public.comments",
        "favorite_folders": "public.favorite_folders",
        "ratings": "public.ratings",
        "comment_likes": "public.comment_likes",
        "favorites": "public.favorites",
        "view_history": "public.view_history",
        "watched_movies": "public.watched_movies",
    },
    "target_table": "dw.dw_user_event_fact_di",
    "sink_path": "hdfs:///warehouse/movie/dw/user_event_fact_di",
}

EVENT_SCHEMA: dict[str, str] = {
    "event_type": "string",
    "occurred_at": "timestamp",
    "ingest_time": "timestamp",
    "user_id": "string",
    "movie_id": "bigint",
    "comment_id": "bigint",
    "operation": "string",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build compact dw_user_event_fact_di from PostgreSQL source tables in T+1 batch mode."
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
        help="Snapshot partition date for legacy Hive source mode. "
        "Ignored when dwd.source_mode=jdbc.",
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


def resolve_jdbc_table_config(pg_config: dict[str, Any], source_table: str) -> dict[str, Any]:
    for table_config in pg_config.get("tables", []):
        if table_config.get("source_table") == source_table:
            resolved_config = copy.deepcopy(table_config)
            resolved_config.pop("target_table", None)
            resolved_config.pop("sink_path", None)
            return resolved_config
    return {"source_table": source_table}


def load_jdbc_table(spark: SparkSession, pg_config: dict[str, Any], source_table: str) -> DataFrame:
    table_config = resolve_jdbc_table_config(pg_config, source_table)
    return build_jdbc_reader(spark, pg_config, table_config).load()


def filter_by_calc_date(df: DataFrame, ts_col: str, calc_date: str) -> DataFrame:
    return df.where(F.to_date(F.col(ts_col)) == F.lit(calc_date).cast("date"))








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
    valid_movie_ids = movies_df.select("movie_id")
    filtered_df = filter_by_calc_date(
        view_history_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull() & F.col("view_time").isNotNull())
        .join(valid_movie_ids, "movie_id", "inner"),
        "view_time",
        calc_date,
    )

    return align_event_schema(
        filtered_df.select(
            F.lit("view_history").alias("event_type"),
            F.col("view_time").alias("occurred_at"),
            F.col("view_time").alias("ingest_time"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
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

    return align_event_schema(
        filtered_df.select(
            F.lit("rating").alias("event_type"),
            F.col("rating_time").alias("occurred_at"),
            F.col("rating_time").alias("ingest_time"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
        )
    )


def build_comment_events(comments_df: DataFrame, movies_df: DataFrame, calc_date: str) -> DataFrame:
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

    return align_event_schema(
        filtered_df.select(
            F.lit("comment").alias("event_type"),
            F.col("comment_time").alias("occurred_at"),
            F.col("comment_time").alias("ingest_time"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("comment_id").cast("bigint").alias("comment_id"),
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

    return align_event_schema(
        joined_df.select(
            F.lit("comment_like").alias("event_type"),
            F.col("cl.create_time").alias("occurred_at"),
            F.col("cl.create_time").alias("ingest_time"),
            F.col("cl.user_id").cast("string").alias("user_id"),
            F.col("c.movie_id").cast("bigint").alias("movie_id"),
            F.col("cl.comment_id").cast("bigint").alias("comment_id"),
        )
    )


def build_favorite_events(favorites_df: DataFrame, movies_df: DataFrame, calc_date: str) -> DataFrame:
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

    return align_event_schema(
        filtered_df.select(
            F.lit("favorite").alias("event_type"),
            F.col("create_time").alias("occurred_at"),
            F.col("create_time").alias("ingest_time"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.lit("ADD").alias("operation"),
        )
    )


def build_watched_events(watched_movies_df: DataFrame, movies_df: DataFrame, calc_date: str) -> DataFrame:
    valid_movie_ids = movies_df.select("movie_id")
    filtered_df = filter_by_calc_date(
        watched_movies_df.where(
            F.col("user_id").isNotNull() & F.col("movie_id").isNotNull() & F.col("create_time").isNotNull()
        )
        .join(valid_movie_ids, "movie_id", "inner"),
        "create_time",
        calc_date,
    )

    return align_event_schema(
        filtered_df.select(
            F.lit("watched").alias("event_type"),
            F.col("create_time").alias("occurred_at"),
            F.col("create_time").alias("ingest_time"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
        )
    )


def build_register_events(users_df: DataFrame, calc_date: str) -> DataFrame:
    filtered_df = filter_by_calc_date(
        users_df.where(F.col("user_id").isNotNull() & F.col("create_time").isNotNull()),
        "create_time",
        calc_date,
    )

    return align_event_schema(
        filtered_df.select(
            F.lit("user_register").alias("event_type"),
            F.col("create_time").alias("occurred_at"),
            F.col("create_time").alias("ingest_time"),
            F.col("user_id").cast("string").alias("user_id"),
        )
    )


def build_favorite_folder_action_events(folders_df: DataFrame, calc_date: str) -> DataFrame:
    base_df = folders_df.where(F.col("id").isNotNull() & F.col("user_id").isNotNull())

    create_df = filter_by_calc_date(base_df.where(F.col("create_time").isNotNull()), "create_time", calc_date).select(
        F.col("user_id").cast("string").alias("user_id"),
        F.col("create_time").alias("event_time"),
        F.lit("CREATE").alias("operation"),
    )

    update_df = filter_by_calc_date(base_df.where(F.col("update_time").isNotNull()), "update_time", calc_date).where(
        F.col("create_time").isNull() | (F.col("update_time") != F.col("create_time"))
    ).select(
        F.col("user_id").cast("string").alias("user_id"),
        F.col("update_time").alias("event_time"),
        F.lit("UPDATE").alias("operation"),
    )

    folder_events_df = union_all([create_df, update_df])

    return align_event_schema(
        folder_events_df.select(
            F.lit("favorite_folder_action").alias("event_type"),
            F.col("event_time").alias("occurred_at"),
            F.col("event_time").alias("ingest_time"),
            F.col("user_id").cast("string").alias("user_id"),
            F.col("operation").cast("string").alias("operation"),
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
    movies_df: DataFrame,
    comments_df: DataFrame,
) -> DataFrame:
    e = events_df.alias("e")
    c = comments_df.alias("c")

    joined_with_comment_df = e.join(c, F.col("e.comment_id") == F.col("c.comment_id"), "left")
    effective_movie_id = F.coalesce(F.col("e.movie_id"), F.col("c.movie_id"))

    joined = joined_with_comment_df.join(movies_df.alias("m"), effective_movie_id == F.col("m.movie_id"), "left")

    event_ts = F.coalesce(F.col("e.occurred_at"), F.col("e.ingest_time"))
    event_type = F.col("e.event_type")

    return joined.select(
        event_ts.alias("event_ts"),
        F.col("e.user_id").alias("user_id"),
        effective_movie_id.cast("bigint").alias("movie_id"),
        F.col("m.genres").alias("movie_genres"),
        F.when(event_type == F.lit("view_history"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_view"),
        F.when(event_type == F.lit("rating"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_rating"),
        F.when(event_type == F.lit("comment"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_comment"),
        F.when(event_type == F.lit("comment_like"), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_comment_like"),
        F.when(event_type == F.lit("favorite"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_favorite"),
        F.when(event_type == F.lit("watched"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_watched"),
        F.when(event_type == F.lit("user_register"), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_register"),
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    pg_config: dict[str, Any] = config["postgres"]
    dw_config = merge_nested_dict(DEFAULT_DW_EVENT_FACT_CONFIG, config.get("dw_event_fact", {}))

    calc_date = args.calc_date
    requested_snapshot_date = args.snapshot_date.strip()

    spark = build_spark_session("movie-dwd-user-event-wide-di", spark_config)
    try:
        source_tables = dw_config["source_tables"]
        source_mode = str(dw_config.get("source_mode", "jdbc")).strip().lower()
        target_table = dw_config["target_table"]
        target_db = target_table.split(".", 1)[0] if "." in target_table else "default"
        if target_db != "default":
            spark.sql(f"CREATE DATABASE IF NOT EXISTS {target_db}")

        if source_mode == "jdbc":
            snapshot_date = requested_snapshot_date or calc_date
            users_df = load_jdbc_table(spark, pg_config, source_tables["users"])
            movies_df = load_jdbc_table(spark, pg_config, source_tables["movies"])
            comments_df = load_jdbc_table(spark, pg_config, source_tables["comments"])
            folders_df = load_jdbc_table(spark, pg_config, source_tables["favorite_folders"])
            ratings_df = load_jdbc_table(spark, pg_config, source_tables["ratings"])
            comment_likes_df = load_jdbc_table(spark, pg_config, source_tables["comment_likes"])
            favorites_df = load_jdbc_table(spark, pg_config, source_tables["favorites"])
            view_history_df = load_jdbc_table(spark, pg_config, source_tables["view_history"])
            watched_movies_df = load_jdbc_table(spark, pg_config, source_tables["watched_movies"])
        elif source_mode in {"hive", "hive_partition", "partition"}:
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
        else:
            raise ValueError(f"Unsupported dwd.source_mode: {source_mode}")

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
        wide_df = build_wide_table(events_df, movies_df, comments_df)

        sink_path = dw_config["sink_path"]
        write_partition(wide_df, target_table, sink_path, calc_date, spark)

        print(
            "Compact fact build finished in PostgreSQL T+1 batch mode. "
            f"source_mode={source_mode}, table={target_table}, dt={calc_date}, source_snapshot_dt={snapshot_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
