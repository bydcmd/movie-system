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
    parser = argparse.ArgumentParser(description="Build DM daily genre preference ranking from compact movie metrics.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument("--top-n", type=int, default=0, help="Override top N genres. 0 means use config.")
    return parser.parse_args()


def build_genre_movie_metrics(movie_metrics_df: DataFrame) -> DataFrame:
    return (
        movie_metrics_df.select(
            "movie_id",
            "view_pv",
            "rating_cnt",
            "watched_cnt",
            F.col("hot_score").cast("double").alias("hot_score_value"),
            F.explode(F.split(F.coalesce(F.col("movie_genres"), F.lit("")), "[,/]")).alias("genre"),
        )
        .withColumn("genre", F.trim(F.col("genre")))
        .where(F.col("genre") != "")
        .groupBy("genre")
        .agg(
            F.countDistinct("movie_id").cast("bigint").alias("movie_cnt"),
            F.sum("view_pv").cast("bigint").alias("view_pv"),
            F.sum("rating_cnt").cast("bigint").alias("rating_cnt"),
            F.sum("watched_cnt").cast("bigint").alias("watched_cnt"),
            F.round(F.sum("hot_score_value"), 4).cast("decimal(18,4)").alias("hot_score_sum"),
        )
    )


def build_genre_preference(movie_metrics_df: DataFrame, top_n: int) -> DataFrame:
    genre_metrics_df = build_genre_movie_metrics(movie_metrics_df)

    rank_window = Window.orderBy(
        F.col("hot_score_sum").desc(), F.col("view_pv").desc(), F.col("genre").asc()
    )
    ranked_df = genre_metrics_df.withColumn("rank_no", F.row_number().over(rank_window))
    if top_n > 0:
        ranked_df = ranked_df.where(F.col("rank_no") <= top_n)

    return ranked_df.select(
        "genre",
        F.col("rank_no").cast("int").alias("rank_no"),
        "movie_cnt",
        "view_pv",
        "view_uv",
        "rating_cnt",
        "watched_cnt",
        "hot_score_sum",
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    dm_config: dict[str, Any] = config["dm_genre_preference"]

    calc_date = args.calc_date
    source_table = dm_config["source_table"]
    target_table = dm_config["target_table"]
    sink_path = dm_config["sink_path"]
    metric_period_type = str(dm_config.get("metric_period_type", "DAILY")).strip().upper()

    top_n = args.top_n if args.top_n > 0 else int(dm_config.get("top_n", 100))
    if top_n <= 0:
        raise ValueError(f"Invalid top_n: {top_n}")

    spark = build_spark_session("movie-dm-genre-preference-1d", spark_config)
    try:
        target_db = target_table.split(".", 1)[0] if "." in target_table else "default"
        if target_db != "default":
            spark.sql(f"CREATE DATABASE IF NOT EXISTS {target_db}")

        movie_metrics_df = spark.table(source_table).where(F.col("dt") == calc_date)
        if "period_type" in movie_metrics_df.columns:
            movie_metrics_df = movie_metrics_df.where(F.col("period_type") == metric_period_type)

        result_df = build_genre_preference(movie_metrics_df, top_n)
        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(
            "DM genre preference build finished. "
            f"source={source_table}, target={target_table}, dt={calc_date}, top_n={top_n}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
