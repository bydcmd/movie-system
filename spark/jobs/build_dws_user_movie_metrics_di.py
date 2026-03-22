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
    parser = argparse.ArgumentParser(description="Build DWS daily aggregates from dwd_user_event_wide_di.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    return parser.parse_args()


def build_user_action_agg(events_df: DataFrame) -> DataFrame:
    rating_value = F.coalesce(F.col("rating"), F.col("rating_snapshot")).cast("double")

    return (
        events_df.where(F.col("user_id").isNotNull())
        .groupBy("user_id")
        .agg(
            F.max("user_nickname").alias("user_nickname"),
            F.max("user_role").cast("tinyint").alias("user_role"),
            F.max("user_status").cast("int").alias("user_status"),
            F.sum(F.col("is_view")).cast("bigint").alias("view_cnt"),
            F.sum(F.col("is_rating")).cast("bigint").alias("rating_cnt"),
            F.round(F.avg(F.when(F.col("is_rating") == 1, rating_value)), 2)
            .cast("decimal(10,2)")
            .alias("rating_avg"),
            F.sum(F.col("is_comment")).cast("bigint").alias("comment_cnt"),
            F.sum(F.col("is_comment_like")).cast("bigint").alias("comment_like_cnt"),
            F.sum(F.when((F.col("is_favorite") == 1) & (F.col("operation_norm") == "ADD"), 1).otherwise(0))
            .cast("bigint")
            .alias("favorite_add_cnt"),
            F.sum(F.when((F.col("is_favorite") == 1) & (F.col("operation_norm") == "REMOVE"), 1).otherwise(0))
            .cast("bigint")
            .alias("favorite_remove_cnt"),
            F.sum(F.col("is_watched")).cast("bigint").alias("watched_cnt"),
            F.sum(F.col("is_search")).cast("bigint").alias("search_cnt"),
            F.sum(F.col("is_login")).cast("bigint").alias("login_cnt"),
            F.sum(F.col("is_register")).cast("bigint").alias("register_cnt"),
            F.sum(F.col("is_favorite_folder_action")).cast("bigint").alias("favorite_folder_action_cnt"),
            F.countDistinct("movie_id").cast("bigint").alias("active_movie_cnt"),
            F.max("event_ts").alias("last_event_ts"),
        )
    )


def build_movie_action_agg(events_df: DataFrame, weights: dict[str, Any]) -> DataFrame:
    rating_value = F.coalesce(F.col("rating"), F.col("rating_snapshot")).cast("double")

    agg_df = (
        events_df.where(F.col("movie_id").isNotNull())
        .groupBy("movie_id")
        .agg(
            F.max("movie_name").alias("movie_name"),
            F.max("movie_year").cast("int").alias("movie_year"),
            F.max("movie_genres").alias("movie_genres"),
            F.max("movie_score").cast("decimal(3,1)").alias("movie_score"),
            F.max("movie_douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
            F.sum(F.col("is_view")).cast("bigint").alias("view_pv"),
            F.countDistinct(F.when(F.col("is_view") == 1, F.col("user_id"))).cast("bigint").alias("view_uv"),
            F.sum(F.col("is_rating")).cast("bigint").alias("rating_cnt"),
            F.round(F.avg(F.when(F.col("is_rating") == 1, rating_value)), 2)
            .cast("decimal(10,2)")
            .alias("rating_avg"),
            F.sum(F.col("is_comment")).cast("bigint").alias("comment_cnt"),
            F.sum(F.col("is_comment_like")).cast("bigint").alias("comment_like_cnt"),
            F.sum(F.when((F.col("is_favorite") == 1) & (F.col("operation_norm") == "ADD"), 1).otherwise(0))
            .cast("bigint")
            .alias("favorite_add_cnt"),
            F.sum(F.when((F.col("is_favorite") == 1) & (F.col("operation_norm") == "REMOVE"), 1).otherwise(0))
            .cast("bigint")
            .alias("favorite_remove_cnt"),
            F.sum(F.col("is_watched")).cast("bigint").alias("watched_cnt"),
            F.countDistinct("user_id").cast("bigint").alias("active_user_cnt"),
            F.max("event_ts").alias("last_event_ts"),
        )
    )

    hot_score = (
        F.col("view_pv") * F.lit(float(weights.get("view_pv", 1.0)))
        + F.col("view_uv") * F.lit(float(weights.get("view_uv", 1.5)))
        + F.col("rating_cnt") * F.lit(float(weights.get("rating_cnt", 2.0)))
        + F.col("comment_cnt") * F.lit(float(weights.get("comment_cnt", 2.0)))
        + F.col("favorite_add_cnt") * F.lit(float(weights.get("favorite_add_cnt", 1.2)))
        + F.col("watched_cnt") * F.lit(float(weights.get("watched_cnt", 2.5)))
    )

    return agg_df.withColumn("hot_score", F.round(hot_score, 4).cast("decimal(18,4)"))


def build_event_type_agg(events_df: DataFrame) -> DataFrame:
    rating_value = F.coalesce(F.col("rating"), F.col("rating_snapshot")).cast("double")

    return (
        events_df.groupBy("event_type")
        .agg(
            F.count(F.lit(1)).cast("bigint").alias("event_cnt"),
            F.countDistinct("user_id").cast("bigint").alias("user_cnt"),
            F.countDistinct("movie_id").cast("bigint").alias("movie_cnt"),
            F.round(F.avg(F.when(F.col("is_rating") == 1, rating_value)), 2)
            .cast("decimal(10,2)")
            .alias("rating_avg"),
            F.max("event_ts").alias("last_event_ts"),
        )
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    dws_config: dict[str, Any] = config["dws"]

    spark = build_spark_session("movie-dws-user-movie-metrics-di", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS dws")

        calc_date = args.calc_date
        source_table = dws_config["source_table"]
        events_df = spark.table(source_table).where(F.col("dt") == calc_date)

        normalized_df = events_df.withColumn(
            "operation_norm",
            F.upper(F.trim(F.coalesce(F.col("operation_norm"), F.col("operation")))),
        )

        target_tables = dws_config["target_tables"]
        sink_paths = dws_config["sink_paths"]
        weights = dws_config.get("hot_score_weights", {})

        user_agg_df = build_user_action_agg(normalized_df)
        movie_agg_df = build_movie_action_agg(normalized_df, weights)
        event_type_agg_df = build_event_type_agg(normalized_df)

        write_partition(
            user_agg_df,
            target_tables["user_action_1d"],
            sink_paths["user_action_1d"],
            calc_date,
            spark,
        )
        write_partition(
            movie_agg_df,
            target_tables["movie_action_1d"],
            sink_paths["movie_action_1d"],
            calc_date,
            spark,
        )
        write_partition(
            event_type_agg_df,
            target_tables["event_type_1d"],
            sink_paths["event_type_1d"],
            calc_date,
            spark,
        )

        print(f"DWS build finished. source={source_table}, dt={calc_date}")
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
