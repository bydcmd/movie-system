from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import Window
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build ADS search keyword insights from DWD events.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument("--top-n", type=int, default=0, help="Override top N keywords. 0 means use config.")
    return parser.parse_args()


def resolve_non_negative_int(raw_value: Any, name: str) -> int:
    value = int(raw_value)
    if value < 0:
        raise ValueError(f"Invalid {name}: {raw_value}")
    return value


def build_keyword_insights(events_df: DataFrame, min_search_cnt: int, top_n: int) -> DataFrame:
    search_events_df = (
        events_df.where((F.col("is_search") == 1) & F.col("user_id").isNotNull())
        .withColumn("search_keyword", F.trim(F.coalesce(F.col("search_keyword"), F.lit(""))))
        .where(F.col("search_keyword") != "")
    )

    keyword_stats_df = (
        search_events_df.groupBy("search_keyword")
        .agg(
            F.count(F.lit(1)).cast("bigint").alias("search_cnt"),
            F.countDistinct("user_id").cast("bigint").alias("search_user_cnt"),
            F.sum(F.when(F.coalesce(F.col("result_count"), F.lit(0)) <= 0, 1).otherwise(0)).cast("bigint").alias("zero_result_cnt"),
            F.round(F.avg(F.coalesce(F.col("result_count"), F.lit(0)).cast("double")), 2).cast("decimal(10,2)").alias("avg_result_count"),
        )
        .where(F.col("search_cnt") >= F.lit(int(min_search_cnt)))
    )

    user_action_df = (
        events_df.where(F.col("user_id").isNotNull())
        .groupBy("user_id")
        .agg(
            F.max(F.coalesce(F.col("is_view"), F.lit(0))).cast("bigint").alias("did_view"),
            F.max(F.coalesce(F.col("is_watched"), F.lit(0))).cast("bigint").alias("did_watch"),
            F.max(F.coalesce(F.col("is_rating"), F.lit(0))).cast("bigint").alias("did_rating"),
        )
    )

    keyword_user_df = search_events_df.select("search_keyword", "user_id").dropDuplicates()
    keyword_user_action_df = keyword_user_df.join(user_action_df, on="user_id", how="left")

    conversion_df = keyword_user_action_df.groupBy("search_keyword").agg(
        F.sum(F.coalesce(F.col("did_view"), F.lit(0))).cast("bigint").alias("after_search_view_user_cnt"),
        F.sum(F.coalesce(F.col("did_watch"), F.lit(0))).cast("bigint").alias("after_search_watch_user_cnt"),
        F.sum(F.coalesce(F.col("did_rating"), F.lit(0))).cast("bigint").alias("after_search_rating_user_cnt"),
    )

    merged_df = keyword_stats_df.join(conversion_df, on="search_keyword", how="left").select(
        F.col("search_keyword"),
        F.col("search_cnt"),
        F.col("search_user_cnt"),
        F.col("zero_result_cnt"),
        F.col("avg_result_count"),
        F.coalesce(F.col("after_search_view_user_cnt"), F.lit(0)).cast("bigint").alias("after_search_view_user_cnt"),
        F.coalesce(F.col("after_search_watch_user_cnt"), F.lit(0)).cast("bigint").alias("after_search_watch_user_cnt"),
        F.coalesce(F.col("after_search_rating_user_cnt"), F.lit(0)).cast("bigint").alias("after_search_rating_user_cnt"),
    )

    scored_df = merged_df.select(
        "search_keyword",
        "search_cnt",
        "search_user_cnt",
        "zero_result_cnt",
        "avg_result_count",
        "after_search_view_user_cnt",
        "after_search_watch_user_cnt",
        "after_search_rating_user_cnt",
        F.when(F.col("search_cnt") > 0, F.round(F.col("zero_result_cnt") / F.col("search_cnt"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("zero_result_rate"),
        F.when(F.col("search_user_cnt") > 0, F.round(F.col("after_search_view_user_cnt") / F.col("search_user_cnt"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("search_to_view_rate"),
        F.when(F.col("after_search_view_user_cnt") > 0, F.round(F.col("after_search_watch_user_cnt") / F.col("after_search_view_user_cnt"), 4))
        .otherwise(F.lit(None))
        .cast("decimal(10,4)")
        .alias("view_to_watch_rate"),
    ).withColumn(
        "problem_score",
        F.round(
            F.coalesce(F.col("zero_result_rate").cast("double"), F.lit(0.0)) * F.lit(0.7)
            + (F.lit(1.0) - F.coalesce(F.col("search_to_view_rate").cast("double"), F.lit(0.0))) * F.lit(0.3),
            4,
        ).cast("decimal(10,4)"),
    )

    rank_window = Window.orderBy(F.col("problem_score").desc(), F.col("search_cnt").desc(), F.col("search_keyword").asc())
    ranked_df = scored_df.withColumn("rank_no", F.row_number().over(rank_window))
    if top_n > 0:
        ranked_df = ranked_df.where(F.col("rank_no") <= F.lit(int(top_n)))

    return ranked_df.select(
        "search_keyword",
        F.col("rank_no").cast("int").alias("rank_no"),
        "search_cnt",
        "search_user_cnt",
        "zero_result_cnt",
        "zero_result_rate",
        "avg_result_count",
        "after_search_view_user_cnt",
        "after_search_watch_user_cnt",
        "after_search_rating_user_cnt",
        "search_to_view_rate",
        "view_to_watch_rate",
        "problem_score",
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    ads_config: dict[str, Any] = config["ads_search_keyword_insights"]

    calc_date = args.calc_date
    source_table = ads_config["source_table"]
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]
    min_search_cnt = resolve_non_negative_int(ads_config.get("min_search_cnt", 5), "min_search_cnt")
    top_n = args.top_n if args.top_n > 0 else resolve_non_negative_int(ads_config.get("top_n", 200), "top_n")

    spark = build_spark_session("movie-ads-search-keyword-insights-1d", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        source_df = spark.table(source_table).where(F.col("dt") == calc_date)
        result_df = build_keyword_insights(source_df, min_search_cnt=min_search_cnt, top_n=top_n)
        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(
            "ADS search keyword insights build finished. "
            f"source={source_table}, target={target_table}, dt={calc_date}, min_search_cnt={min_search_cnt}, top_n={top_n}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
