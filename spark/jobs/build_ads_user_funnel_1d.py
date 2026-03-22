from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build ADS daily user funnel metrics from DWD wide events.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    return parser.parse_args()


def build_user_funnel(events_df: DataFrame) -> DataFrame:
    user_level_df = (
        events_df.where(F.col("user_id").isNotNull())
        .groupBy("user_id")
        .agg(
            F.max(F.coalesce(F.col("is_view"), F.lit(0))).cast("bigint").alias("did_view"),
            F.max(F.coalesce(F.col("is_rating"), F.lit(0))).cast("bigint").alias("did_rating"),
            F.max(F.coalesce(F.col("is_comment"), F.lit(0))).cast("bigint").alias("did_comment"),
            F.max(
                F.when(
                    (F.coalesce(F.col("is_favorite"), F.lit(0)) == 1)
                    & (
                        F.upper(F.trim(F.coalesce(F.col("operation_norm"), F.col("operation"), F.lit(""))))
                        == F.lit("ADD")
                    ),
                    F.lit(1),
                ).otherwise(F.lit(0))
            )
            .cast("bigint")
            .alias("did_favorite"),
            F.max(F.coalesce(F.col("is_favorite_folder_action"), F.lit(0))).cast("bigint").alias("did_favorite_folder_action"),
            F.max(F.coalesce(F.col("is_watched"), F.lit(0))).cast("bigint").alias("did_watched"),
        )
    )

    metrics_df = user_level_df.agg(
        F.count(F.lit(1)).cast("bigint").alias("total_active_users"),
        F.sum("did_view").cast("bigint").alias("view_users"),
        F.sum("did_rating").cast("bigint").alias("rating_users"),
        F.sum("did_comment").cast("bigint").alias("comment_users"),
        F.sum("did_favorite").cast("bigint").alias("favorite_users"),
        F.sum("did_favorite_folder_action").cast("bigint").alias("favorite_folder_action_users"),
        F.sum("did_watched").cast("bigint").alias("watched_users"),
        F.sum(F.when((F.col("did_view") == 1) & (F.col("did_rating") == 1), 1).otherwise(0))
        .cast("bigint")
        .alias("view_to_rating_users"),
        F.sum(F.when((F.col("did_rating") == 1) & (F.col("did_comment") == 1), 1).otherwise(0))
        .cast("bigint")
        .alias("rating_to_comment_users"),
        F.sum(F.when((F.col("did_comment") == 1) & (F.col("did_favorite") == 1), 1).otherwise(0))
        .cast("bigint")
        .alias("comment_to_favorite_users"),
        F.sum(F.when((F.col("did_favorite") == 1) & (F.col("did_watched") == 1), 1).otherwise(0))
        .cast("bigint")
        .alias("favorite_to_watched_users"),
    ).select(
        F.col("total_active_users"),
        F.coalesce(F.col("view_users"), F.lit(0)).cast("bigint").alias("view_users"),
        F.coalesce(F.col("rating_users"), F.lit(0)).cast("bigint").alias("rating_users"),
        F.coalesce(F.col("comment_users"), F.lit(0)).cast("bigint").alias("comment_users"),
        F.coalesce(F.col("favorite_users"), F.lit(0)).cast("bigint").alias("favorite_users"),
        F.coalesce(F.col("favorite_folder_action_users"), F.lit(0)).cast("bigint").alias("favorite_folder_action_users"),
        F.coalesce(F.col("watched_users"), F.lit(0)).cast("bigint").alias("watched_users"),
        F.coalesce(F.col("view_to_rating_users"), F.lit(0)).cast("bigint").alias("view_to_rating_users"),
        F.coalesce(F.col("rating_to_comment_users"), F.lit(0)).cast("bigint").alias("rating_to_comment_users"),
        F.coalesce(F.col("comment_to_favorite_users"), F.lit(0)).cast("bigint").alias("comment_to_favorite_users"),
        F.coalesce(F.col("favorite_to_watched_users"), F.lit(0)).cast("bigint").alias("favorite_to_watched_users"),
    )

    return metrics_df.select(
        "total_active_users",
        "view_users",
        "rating_users",
        "comment_users",
        "favorite_users",
        "favorite_folder_action_users",
        "watched_users",
        F.when(F.col("view_users") > 0, F.round(F.col("view_to_rating_users") / F.col("view_users"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("view_to_rating_rate"),
        F.when(F.col("rating_users") > 0, F.round(F.col("rating_to_comment_users") / F.col("rating_users"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("rating_to_comment_rate"),
        F.when(F.col("comment_users") > 0, F.round(F.col("comment_to_favorite_users") / F.col("comment_users"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("comment_to_favorite_rate"),
        F.when(F.col("favorite_users") > 0, F.round(F.col("favorite_to_watched_users") / F.col("favorite_users"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("favorite_to_watched_rate"),
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    ads_config: dict[str, Any] = config["ads_user_funnel"]

    calc_date = args.calc_date
    source_table = ads_config["source_table"]
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]

    spark = build_spark_session("movie-ads-user-funnel-1d", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        source_df = spark.table(source_table).where(F.col("dt") == calc_date)
        result_df = build_user_funnel(source_df)
        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(f"ADS user funnel build finished. source={source_table}, target={target_table}, dt={calc_date}")
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
