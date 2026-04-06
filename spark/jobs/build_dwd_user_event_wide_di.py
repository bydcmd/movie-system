from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import functions as F
from pyspark.sql import Window

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import resolve_common_dt_partition_date, write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build dwd_user_event_wide_di from ODS layers.")
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


def load_partition(spark, table_name: str, partition_date: str) -> DataFrame:
    return spark.table(table_name).where(F.col("dt") == partition_date)


def deduplicate_events(events_df: DataFrame) -> DataFrame:
    dedup_key = F.coalesce(
        F.col("event_id"),
        F.sha2(
            F.concat_ws(
                "||",
                F.coalesce(F.col("topic"), F.lit("")),
                F.coalesce(F.col("event_key"), F.lit("")),
                F.coalesce(F.col("raw_json"), F.lit("")),
                F.coalesce(F.col("hh"), F.lit("")),
            ),
            256,
        ),
    )
    ranked = events_df.withColumn("dedup_key", dedup_key).withColumn(
        "rn",
        F.row_number().over(
            Window.partitionBy(F.col("dedup_key"))
            .orderBy(F.col("ingest_time").desc_nulls_last(), F.col("kafka_timestamp").desc_nulls_last())
        ),
    )
    return ranked.where(F.col("rn") == 1).drop("dedup_key", "rn")


def resolve_event_column(
    events_df: DataFrame,
    event_alias: str,
    column_name: str,
    json_path: str,
    cast_type: str | None = None,
) -> F.Column:
    if column_name in events_df.columns:
        column = F.col(f"{event_alias}.{column_name}")
    else:
        column = F.get_json_object(F.col(f"{event_alias}.raw_json"), json_path)
    if cast_type:
        column = column.cast(cast_type)
    return column


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
    m = movies_df.alias("m")
    c = comments_df.alias("c")
    f = folders_df.alias("f")
    r = ratings_df.alias("r")

    joined = (
        e.join(u, F.col("e.user_id") == F.col("u.user_id"), "left")
        .join(m, F.col("e.movie_id") == F.col("m.movie_id"), "left")
        .join(c, F.col("e.comment_id") == F.col("c.comment_id"), "left")
        .join(f, F.col("e.folder_id") == F.col("f.id"), "left")
        .join(
            r,
            (F.col("e.user_id") == F.col("r.user_id")) & (F.col("e.movie_id") == F.col("r.movie_id")),
            "left",
        )
    )

    event_ts = F.coalesce(F.col("e.occurred_at"), F.col("e.kafka_timestamp"), F.col("e.ingest_time"))
    event_type = F.col("e.event_type")
    event_operation = resolve_event_column(events_df, "e", "operation", "$.data.operation")
    event_rating = resolve_event_column(events_df, "e", "rating", "$.data.rating", "int")
    event_search_keyword = resolve_event_column(events_df, "e", "search_keyword", "$.data.searchKeyword")
    event_result_count = resolve_event_column(events_df, "e", "result_count", "$.data.resultCount", "bigint")
    event_filter_conditions = resolve_event_column(
        events_df, "e", "filter_conditions", "$.data.filterConditions"
    )
    event_search_time = resolve_event_column(events_df, "e", "search_time", "$.data.searchTime", "bigint")
    event_folder_name = resolve_event_column(events_df, "e", "folder_name", "$.data.folderName")
    event_folder_is_public = resolve_event_column(
        events_df, "e", "folder_is_public", "$.data.isPublic", "tinyint"
    )

    return joined.select(
        F.col("e.topic").alias("topic"),
        F.col("e.event_key").alias("event_key"),
        F.col("e.event_id").alias("event_id"),
        event_type.alias("event_type"),
        event_ts.alias("event_ts"),
        F.col("e.kafka_timestamp").alias("kafka_timestamp"),
        F.col("e.occurred_at").alias("occurred_at"),
        F.col("e.ingest_time").alias("ingest_time"),
        F.col("e.hh").alias("hh"),
        F.col("e.user_id").alias("user_id"),
        F.col("u.user_nickname").alias("user_nickname"),
        F.col("u.role").cast("tinyint").alias("user_role"),
        F.col("u.status").cast("int").alias("user_status"),
        F.col("e.movie_id").alias("movie_id"),
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
        F.coalesce(event_folder_name, F.col("f.name")).alias("folder_name"),
        F.coalesce(event_folder_is_public, F.col("f.is_public")).cast("tinyint").alias("folder_is_public"),
        event_operation.alias("operation"),
        F.upper(F.trim(event_operation)).alias("operation_norm"),
        event_rating.alias("rating"),
        F.col("r.rating").cast("int").alias("rating_snapshot"),
        F.col("r.rating_time").alias("rating_time"),
        event_search_keyword.alias("search_keyword"),
        event_result_count.alias("result_count"),
        event_filter_conditions.alias("filter_conditions"),
        event_search_time.alias("search_time"),
        F.when(event_type == F.lit("view_history"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_view"),
        F.when(event_type == F.lit("rating"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_rating"),
        F.when(event_type == F.lit("comment"), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_comment"),
        F.when(event_type == F.lit("comment_like"), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_comment_like"),
        F.when(event_type == F.lit("favorite"), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_favorite"),
        F.when(event_type == F.lit("watched"), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_watched"),
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
        # Session tracking fields from event table
        F.col("e.session_id").alias("session_id"),
        F.col("e.page_url").alias("page_url"),
        F.col("e.sequence_number").alias("sequence_number"),
        F.col("e.client_timestamp").alias("client_timestamp"),
        # Session context fields from event table
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
    dwd_config: dict[str, Any] = config["dwd"]

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
            ],
            requested_snapshot_date,
            spark,
            fallback_max_date=calc_date,
        )
        events_df = load_partition(spark, source_tables["events"], calc_date)
        users_df = load_partition(spark, source_tables["users"], snapshot_date)
        movies_df = load_partition(spark, source_tables["movies"], snapshot_date)
        comments_df = load_partition(spark, source_tables["comments"], snapshot_date)
        folders_df = load_partition(spark, source_tables["favorite_folders"], snapshot_date)
        ratings_df = load_partition(spark, source_tables["ratings"], snapshot_date)

        events_df = deduplicate_events(events_df)
        wide_df = build_wide_table(events_df, users_df, movies_df, comments_df, folders_df, ratings_df)

        target_table = dwd_config["target_table"]
        sink_path = dwd_config["sink_path"]
        write_partition(wide_df, target_table, sink_path, calc_date, spark)

        print(
            f"DWD build finished. table={target_table}, dt={calc_date}, source_snapshot_dt={snapshot_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
