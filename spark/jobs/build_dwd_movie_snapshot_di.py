from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import Window
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import assert_non_empty_partition, resolve_dt_partition_date, write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build dwd_movie_snapshot_di from PostgreSQL ODS snapshot.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument(
        "--snapshot-date",
        default="",
        help="Snapshot partition date for PostgreSQL ODS full table. "
        "If omitted, use the latest available dt partition not newer than calc-date.",
    )
    return parser.parse_args()


def load_partition(spark, table_name: str, partition_date: str) -> DataFrame:
    return spark.table(table_name).where(F.col("dt") == partition_date)


def deduplicate_movies(movies_df: DataFrame) -> DataFrame:
    rank_window = Window.partitionBy("movie_id").orderBy(
        F.col("year").desc_nulls_last(),
        F.col("release_date").desc_nulls_last(),
        F.col("name").asc_nulls_last(),
    )
    return (
        movies_df.where(F.col("movie_id").isNotNull())
        .withColumn("rn", F.row_number().over(rank_window))
        .where(F.col("rn") == 1)
        .drop("rn")
    )


def build_movie_snapshot(movies_df: DataFrame, source_snapshot_dt: str) -> DataFrame:
    return movies_df.select(
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.col("name").alias("movie_name"),
        F.col("alias").alias("movie_alias"),
        F.col("actors").alias("movie_actors"),
        F.col("cover").alias("movie_cover"),
        F.col("directors").alias("movie_directors"),
        F.col("douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
        F.col("score").cast("decimal(3,1)").alias("movie_score"),
        F.col("douban_votes").cast("int").alias("movie_douban_votes"),
        F.col("votes").cast("int").alias("movie_votes"),
        F.col("genres").alias("movie_genres"),
        F.col("imdb_id").alias("imdb_id"),
        F.col("languages").alias("movie_languages"),
        F.col("mins").alias("movie_duration_mins"),
        F.col("regions").alias("movie_regions"),
        F.col("release_date").alias("release_date"),
        F.col("storyline").alias("storyline"),
        F.col("year").cast("int").alias("movie_year"),
        F.col("writers").alias("movie_writers"),
        F.col("rating_weights").alias("rating_weights"),
        F.col("full_search_text").alias("full_search_text"),
        F.lit(source_snapshot_dt).alias("source_snapshot_dt"),
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    snapshot_config: dict[str, Any] = config["dwd_movie_snapshot"]

    calc_date = args.calc_date
    requested_snapshot_date = args.snapshot_date.strip()

    spark = build_spark_session("movie-dwd-movie-snapshot-di", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS dwd")

        snapshot_date = resolve_dt_partition_date(
            snapshot_config["source_table"],
            requested_snapshot_date,
            spark,
            fallback_max_date=calc_date,
        )
        source_df = load_partition(spark, snapshot_config["source_table"], snapshot_date)
        assert_non_empty_partition(source_df, snapshot_config["source_table"], {"dt": snapshot_date})
        result_df = build_movie_snapshot(deduplicate_movies(source_df), snapshot_date)

        write_partition(
            result_df,
            snapshot_config["target_table"],
            snapshot_config["sink_path"],
            calc_date,
            spark,
        )

        print(
            "DWD movie snapshot build finished. "
            f"source={snapshot_config['source_table']}, target={snapshot_config['target_table']}, "
            f"dt={calc_date}, source_snapshot_dt={snapshot_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
