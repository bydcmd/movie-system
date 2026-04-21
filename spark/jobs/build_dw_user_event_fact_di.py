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
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


DEFAULT_DW_EVENT_FACT_CONFIG: dict[str, Any] = {
    "source_tables": {
        "users": "public.users",
        "movies": "public.movies",
        "comments": "public.comments",
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
    return parser.parse_args()


def merge_nested_dict(defaults: dict[str, Any], overrides: dict[str, Any]) -> dict[str, Any]:
    merged = copy.deepcopy(defaults)
    for key, value in overrides.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = merge_nested_dict(merged[key], value)
        else:
            merged[key] = value
    return merged


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


def build_rating_events(ratings_df: DataFrame, movies_df: DataFrame, calc_date: str) -> DataFrame:
    valid_movie_ids = movies_df.select("movie_id")
    filtered_df = filter_by_calc_date(
        ratings_df.where(
            F.col("user_id").isNotNull()
            & F.col("movie_id").isNotNull()
            & F.col("rating").isNotNull()
            & F.col("rating_time").isNotNull()
        ).join(valid_movie_ids, "movie_id", "inner"),
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


def build_comment_like_events(
    comment_likes_df: DataFrame,
    comments_df: DataFrame,
    movies_df: DataFrame,
    calc_date: str,
) -> DataFrame:
    valid_movie_ids = movies_df.select("movie_id")
    valid_comments_df = (
        comments_df.where(F.col("comment_id").isNotNull() & F.col("movie_id").isNotNull())
        .join(valid_movie_ids, "movie_id", "inner")
        .select(F.col("comment_id").alias("comment_id"), F.col("movie_id").alias("movie_id"))
        .dropDuplicates(["comment_id"])
    )

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
        valid_comments_df.alias("c"),
        F.col("cl.comment_id") == F.col("c.comment_id"),
        "inner",
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





def build_postgres_events(
    calc_date: str,
    users_df: DataFrame,
    comments_df: DataFrame,
    ratings_df: DataFrame,
    comment_likes_df: DataFrame,
    favorites_df: DataFrame,
    view_history_df: DataFrame,
    watched_movies_df: DataFrame,
    movies_df: DataFrame,
) -> DataFrame:
    event_frames = [
        build_view_history_events(view_history_df, movies_df, calc_date),
        build_rating_events(ratings_df, movies_df, calc_date),
        build_comment_events(comments_df, movies_df, calc_date),
        build_comment_like_events(comment_likes_df, comments_df, movies_df, calc_date),
        build_favorite_events(favorites_df, movies_df, calc_date),
        build_watched_events(watched_movies_df, movies_df, calc_date),
        build_register_events(users_df, calc_date),
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

    spark = build_spark_session("movie-dwd-user-event-wide-di", spark_config)
    try:
        source_tables = dw_config["source_tables"]
        target_table = dw_config["target_table"]
        target_db = target_table.split(".", 1)[0] if "." in target_table else "default"
        if target_db != "default":
            spark.sql(f"CREATE DATABASE IF NOT EXISTS {target_db}")

        users_df = load_jdbc_table(spark, pg_config, source_tables["users"])
        movies_df = load_jdbc_table(spark, pg_config, source_tables["movies"])
        comments_df = load_jdbc_table(spark, pg_config, source_tables["comments"])
        ratings_df = load_jdbc_table(spark, pg_config, source_tables["ratings"])
        comment_likes_df = load_jdbc_table(spark, pg_config, source_tables["comment_likes"])
        favorites_df = load_jdbc_table(spark, pg_config, source_tables["favorites"])
        view_history_df = load_jdbc_table(spark, pg_config, source_tables["view_history"])
        watched_movies_df = load_jdbc_table(spark, pg_config, source_tables["watched_movies"])

        events_df = build_postgres_events(
            calc_date=calc_date,
            users_df=users_df,
            comments_df=comments_df,
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
            f"Compact fact build finished. table={target_table}, dt={calc_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
