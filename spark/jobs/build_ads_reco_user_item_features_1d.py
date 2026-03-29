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
    parser = argparse.ArgumentParser(description="Build ADS user-item preference features from DWD events.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    return parser.parse_args()


def resolve_positive_int(raw_value: Any, name: str) -> int:
    value = int(raw_value)
    if value <= 0:
        raise ValueError(f"Invalid {name}: {raw_value}")
    return value


def build_features(events_df: DataFrame, weights: dict[str, Any], min_feature_score: float) -> DataFrame:
    rating_value = F.coalesce(F.col("rating"), F.col("rating_snapshot")).cast("double")
    operation_norm = F.upper(F.trim(F.coalesce(F.col("operation_norm"), F.col("operation"))))

    feature_score_expr = (
        F.coalesce(F.col("is_view"), F.lit(0)) * F.lit(float(weights.get("view", 1.0)))
        + F.coalesce(F.col("is_comment"), F.lit(0)) * F.lit(float(weights.get("comment", 2.0)))
        + F.coalesce(F.col("is_rating"), F.lit(0)) * F.lit(float(weights.get("rating", 3.0)))
        + F.coalesce(F.col("is_watched"), F.lit(0)) * F.lit(float(weights.get("watched", 3.0)))
        + F.when((F.col("is_favorite") == 1) & (operation_norm == "ADD"), F.lit(float(weights.get("favorite_add", 2.0))))
        .when((F.col("is_favorite") == 1) & (operation_norm == "REMOVE"), F.lit(float(weights.get("favorite_remove", -1.0))))
        .otherwise(F.lit(0.0))
        + F.when(F.col("is_rating") == 1, F.coalesce(rating_value, F.lit(0.0)) / F.lit(5.0)).otherwise(F.lit(0.0))
        * F.lit(float(weights.get("rating_value", 2.0)))
    )

    return (
        events_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .withColumn("operation_norm", operation_norm)
        .withColumn("feature_delta", feature_score_expr.cast("double"))
        .groupBy("user_id", "movie_id")
        .agg(
            F.sum("feature_delta").cast("double").alias("feature_score_raw"),
            F.sum(F.coalesce(F.col("is_view"), F.lit(0))).cast("bigint").alias("view_cnt"),
            F.sum(F.coalesce(F.col("is_rating"), F.lit(0))).cast("bigint").alias("rating_cnt"),
            F.sum(F.coalesce(F.col("is_comment"), F.lit(0))).cast("bigint").alias("comment_cnt"),
            F.sum(F.when((F.col("is_favorite") == 1) & (F.col("operation_norm") == "ADD"), 1).otherwise(0))
            .cast("bigint")
            .alias("favorite_add_cnt"),
            F.sum(F.when((F.col("is_favorite") == 1) & (F.col("operation_norm") == "REMOVE"), 1).otherwise(0))
            .cast("bigint")
            .alias("favorite_remove_cnt"),
            F.sum(F.coalesce(F.col("is_watched"), F.lit(0))).cast("bigint").alias("watched_cnt"),
            F.max("event_ts").alias("last_event_ts"),
        )
        .where(F.col("feature_score_raw") >= F.lit(float(min_feature_score)))
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.round(F.col("feature_score_raw"), 8).cast("decimal(18,8)").alias("feature_score"),
            "view_cnt",
            "rating_cnt",
            "comment_cnt",
            "favorite_add_cnt",
            "favorite_remove_cnt",
            "watched_cnt",
            "last_event_ts",
        )
    )


def build_features_from_preference_snapshot(source_df: DataFrame, min_feature_score: float) -> DataFrame:
    return (
        source_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .where(F.col("preference_score").cast("double") >= F.lit(float(min_feature_score)))
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("preference_score").cast("decimal(18,8)").alias("feature_score"),
            F.coalesce(F.col("view_cnt"), F.lit(0)).cast("bigint").alias("view_cnt"),
            F.coalesce(F.col("rating_cnt"), F.lit(0)).cast("bigint").alias("rating_cnt"),
            F.coalesce(F.col("comment_cnt"), F.lit(0)).cast("bigint").alias("comment_cnt"),
            F.coalesce(F.col("favorite_add_cnt"), F.lit(0)).cast("bigint").alias("favorite_add_cnt"),
            F.coalesce(F.col("favorite_remove_cnt"), F.lit(0)).cast("bigint").alias("favorite_remove_cnt"),
            F.coalesce(F.col("watched_cnt"), F.lit(0)).cast("bigint").alias("watched_cnt"),
            F.col("last_event_ts").alias("last_event_ts"),
        )
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    ads_config: dict[str, Any] = config["ads_reco_user_item_features"]

    calc_date = args.calc_date
    lookback_days = resolve_positive_int(ads_config.get("lookback_days", 30), "lookback_days")
    min_feature_score = float(ads_config.get("min_feature_score", 0.1))
    source_table = ads_config["source_table"]
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]
    weights = ads_config.get("event_score_weights", {})
    source_type = str(ads_config.get("source_type", "event_wide")).strip().lower()

    spark = build_spark_session("movie-ads-reco-user-item-features-1d", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        if source_type == "user_item_preference":
            source_df = spark.table(source_table).where(F.col("dt") == calc_date)
            result_df = build_features_from_preference_snapshot(source_df, min_feature_score)
        else:
            start_date = (dt.date.fromisoformat(calc_date) - dt.timedelta(days=lookback_days - 1)).isoformat()
            source_df = spark.table(source_table).where((F.col("dt") >= start_date) & (F.col("dt") <= calc_date))
            result_df = build_features(source_df, weights, min_feature_score)

        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(
            "ADS reco user-item feature build finished. "
            f"source={source_table}, source_type={source_type}, target={target_table}, dt={calc_date}, lookback_days={lookback_days}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
