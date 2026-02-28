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
    parser = argparse.ArgumentParser(description="Build ADS search funnel metrics from DWD events.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    return parser.parse_args()


def build_search_funnel(events_df: DataFrame) -> DataFrame:
    search_events_df = events_df.where((F.col("is_search") == 1) & F.col("user_id").isNotNull())
    search_user_df = search_events_df.select("user_id").dropDuplicates()

    search_stats_df = search_events_df.agg(
        F.count(F.lit(1)).cast("bigint").alias("search_cnt"),
        F.sum(F.when(F.coalesce(F.col("result_count"), F.lit(0)) > 0, 1).otherwise(0)).cast("bigint").alias("search_with_result_cnt"),
        F.sum(F.when(F.coalesce(F.col("result_count"), F.lit(0)) <= 0, 1).otherwise(0)).cast("bigint").alias("search_zero_result_cnt"),
    )
    search_user_cnt_df = search_user_df.agg(F.count(F.lit(1)).cast("bigint").alias("search_user_cnt"))

    search_user_action_df = (
        search_user_df.join(events_df.where(F.col("user_id").isNotNull()), on="user_id", how="left")
        .groupBy("user_id")
        .agg(
            F.max(F.coalesce(F.col("is_view"), F.lit(0))).cast("bigint").alias("did_view"),
            F.max(F.coalesce(F.col("is_rating"), F.lit(0))).cast("bigint").alias("did_rating"),
            F.max(F.coalesce(F.col("is_watched"), F.lit(0))).cast("bigint").alias("did_watched"),
            F.max(F.coalesce(F.col("is_favorite"), F.lit(0))).cast("bigint").alias("did_favorite"),
        )
    )

    conversion_df = search_user_action_df.agg(
        F.sum(F.coalesce(F.col("did_view"), F.lit(0))).cast("bigint").alias("after_search_view_user_cnt"),
        F.sum(F.coalesce(F.col("did_rating"), F.lit(0))).cast("bigint").alias("after_search_rating_user_cnt"),
        F.sum(F.coalesce(F.col("did_favorite"), F.lit(0))).cast("bigint").alias("after_search_favorite_user_cnt"),
        F.sum(F.coalesce(F.col("did_watched"), F.lit(0))).cast("bigint").alias("after_search_watched_user_cnt"),
    )

    base_df = search_user_cnt_df.crossJoin(search_stats_df).crossJoin(conversion_df)
    normalized_df = base_df.select(
        F.coalesce(F.col("search_user_cnt"), F.lit(0)).cast("bigint").alias("search_user_cnt"),
        F.coalesce(F.col("search_cnt"), F.lit(0)).cast("bigint").alias("search_cnt"),
        F.coalesce(F.col("search_with_result_cnt"), F.lit(0)).cast("bigint").alias("search_with_result_cnt"),
        F.coalesce(F.col("search_zero_result_cnt"), F.lit(0)).cast("bigint").alias("search_zero_result_cnt"),
        F.coalesce(F.col("after_search_view_user_cnt"), F.lit(0)).cast("bigint").alias("after_search_view_user_cnt"),
        F.coalesce(F.col("after_search_rating_user_cnt"), F.lit(0)).cast("bigint").alias("after_search_rating_user_cnt"),
        F.coalesce(F.col("after_search_favorite_user_cnt"), F.lit(0)).cast("bigint").alias("after_search_favorite_user_cnt"),
        F.coalesce(F.col("after_search_watched_user_cnt"), F.lit(0)).cast("bigint").alias("after_search_watched_user_cnt"),
    )

    return normalized_df.select(
        "search_user_cnt",
        "search_cnt",
        "search_with_result_cnt",
        "search_zero_result_cnt",
        "after_search_view_user_cnt",
        "after_search_rating_user_cnt",
        "after_search_favorite_user_cnt",
        "after_search_watched_user_cnt",
        F.when(F.col("search_user_cnt") > 0, F.round(F.col("after_search_view_user_cnt") / F.col("search_user_cnt"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("search_to_view_rate"),
        F.when(F.col("after_search_view_user_cnt") > 0, F.round(F.col("after_search_watched_user_cnt") / F.col("after_search_view_user_cnt"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("view_to_watched_rate"),
        F.when(F.col("search_user_cnt") > 0, F.round(F.col("after_search_rating_user_cnt") / F.col("search_user_cnt"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("search_to_rating_rate"),
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    ads_config: dict[str, Any] = config["ads_search_funnel"]

    calc_date = args.calc_date
    source_table = ads_config["source_table"]
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]

    spark = build_spark_session("movie-ads-search-funnel-1d", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        source_df = spark.table(source_table).where(F.col("dt") == calc_date)
        result_df = build_search_funnel(source_df)
        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(f"ADS search funnel build finished. source={source_table}, target={target_table}, dt={calc_date}")
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
