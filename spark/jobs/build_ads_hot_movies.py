from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import Window
from pyspark.sql.column import Column
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build ADS hot movie rankings from DWS daily aggregates.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument("--top-n", type=int, default=0, help="Override top N rows per period. 0 means use config.")
    return parser.parse_args()


def resolve_period_days(period_days: dict[str, Any] | None) -> dict[str, int]:
    defaults = {"DAILY": 1, "WEEKLY": 7, "MONTHLY": 30}
    if not period_days:
        return defaults

    resolved: dict[str, int] = {}
    for period, default_days in defaults.items():
        raw_value = period_days.get(period, default_days)
        value = int(raw_value)
        if value <= 0:
            raise ValueError(f"Invalid period days for {period}: {raw_value}")
        resolved[period] = value
    return resolved


def build_hot_score_expr(weights: dict[str, Any]) -> Column:
    return (
        F.col("view_pv") * F.lit(float(weights.get("view_pv", 1.0)))
        + F.col("view_uv") * F.lit(float(weights.get("view_uv", 1.5)))
        + F.col("rating_cnt") * F.lit(float(weights.get("rating_cnt", 2.0)))
        + F.col("comment_cnt") * F.lit(float(weights.get("comment_cnt", 2.0)))
        + F.col("comment_like_cnt") * F.lit(float(weights.get("comment_like_cnt", 0.8)))
        + F.col("favorite_add_cnt") * F.lit(float(weights.get("favorite_add_cnt", 1.2)))
        + F.col("favorite_remove_cnt") * F.lit(float(weights.get("favorite_remove_cnt", -0.8)))
        + F.col("watched_cnt") * F.lit(float(weights.get("watched_cnt", 2.5)))
        + F.col("active_user_cnt") * F.lit(float(weights.get("active_user_cnt", 1.0)))
    )


def build_period_hot_ranking(
    source_df: DataFrame,
    calc_date: str,
    period_type: str,
    period_days: int,
    top_n: int,
    weights: dict[str, Any],
) -> DataFrame:
    calc_date_obj = dt.date.fromisoformat(calc_date)
    start_date_obj = calc_date_obj - dt.timedelta(days=period_days - 1)
    start_date = start_date_obj.isoformat()

    filtered_df = source_df.where(
        (F.col("dt_date") >= F.lit(start_date).cast("date")) & (F.col("dt_date") <= F.lit(calc_date).cast("date"))
    )

    rating_sum = (
        F.sum(F.coalesce(F.col("rating_avg").cast("double"), F.lit(0.0)) * F.coalesce(F.col("rating_cnt"), F.lit(0)))
        .cast("double")
        .alias("rating_sum")
    )

    aggregated_df = (
        filtered_df.groupBy("movie_id")
        .agg(
            F.max("movie_name").alias("movie_name"),
            F.max("movie_year").cast("int").alias("movie_year"),
            F.max("movie_genres").alias("movie_genres"),
            F.max("movie_score").cast("decimal(3,1)").alias("movie_score"),
            F.max("movie_douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
            F.sum("view_pv").cast("bigint").alias("view_pv"),
            F.sum("view_uv").cast("bigint").alias("view_uv"),
            F.sum("rating_cnt").cast("bigint").alias("rating_cnt"),
            rating_sum,
            F.sum("comment_cnt").cast("bigint").alias("comment_cnt"),
            F.sum("comment_like_cnt").cast("bigint").alias("comment_like_cnt"),
            F.sum("favorite_add_cnt").cast("bigint").alias("favorite_add_cnt"),
            F.sum("favorite_remove_cnt").cast("bigint").alias("favorite_remove_cnt"),
            F.sum("watched_cnt").cast("bigint").alias("watched_cnt"),
            F.sum("active_user_cnt").cast("bigint").alias("active_user_cnt"),
            F.max("last_event_ts").alias("last_event_ts"),
        )
        .withColumn(
            "rating_avg",
            F.when(F.col("rating_cnt") > 0, F.round(F.col("rating_sum") / F.col("rating_cnt"), 2))
            .otherwise(F.lit(None))
            .cast("decimal(10,2)"),
        )
        .drop("rating_sum")
        .withColumn("period_type", F.lit(period_type))
        .withColumn("window_start", F.lit(start_date))
        .withColumn("window_end", F.lit(calc_date))
        .withColumn("hot_score", F.round(build_hot_score_expr(weights), 4).cast("decimal(18,4)"))
    )

    rank_window = Window.partitionBy("period_type").orderBy(
        F.col("hot_score").desc(),
        F.col("view_pv").desc(),
        F.col("movie_id").asc(),
    )

    ranked_df = aggregated_df.withColumn("rank_no", F.row_number().over(rank_window))
    return ranked_df.where(F.col("rank_no") <= top_n).select(
        "movie_id",
        "movie_name",
        "movie_year",
        "movie_genres",
        "movie_score",
        "movie_douban_score",
        "period_type",
        "rank_no",
        "hot_score",
        "view_pv",
        "view_uv",
        "rating_cnt",
        "rating_avg",
        "comment_cnt",
        "comment_like_cnt",
        "favorite_add_cnt",
        "favorite_remove_cnt",
        "watched_cnt",
        "active_user_cnt",
        "window_start",
        "window_end",
        "last_event_ts",
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    ads_config: dict[str, Any] = config["ads"]

    calc_date = args.calc_date
    source_table = ads_config["source_table"]
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]

    top_n = args.top_n if args.top_n > 0 else int(ads_config.get("top_n", 100))
    if top_n <= 0:
        raise ValueError(f"Invalid top_n: {top_n}")

    period_days = resolve_period_days(ads_config.get("period_days"))
    weights = ads_config.get("hot_score_weights", {})

    spark = build_spark_session("movie-ads-hot-movies", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        max_days = max(period_days.values())
        min_date = (dt.date.fromisoformat(calc_date) - dt.timedelta(days=max_days - 1)).isoformat()
        source_df = (
            spark.table(source_table)
            .where((F.col("dt") >= min_date) & (F.col("dt") <= calc_date))
            .withColumn("dt_date", F.to_date(F.col("dt")))
        )

        period_frames: list[DataFrame] = []
        for period_type, days in period_days.items():
            period_frames.append(
                build_period_hot_ranking(
                    source_df=source_df,
                    calc_date=calc_date,
                    period_type=period_type,
                    period_days=days,
                    top_n=top_n,
                    weights=weights,
                )
            )

        result_df = period_frames[0]
        for period_df in period_frames[1:]:
            result_df = result_df.unionByName(period_df)

        write_partition(result_df, target_table, sink_path, calc_date, spark)
        print(f"ADS hot ranking build finished. source={source_table}, target={target_table}, dt={calc_date}, top_n={top_n}")
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
