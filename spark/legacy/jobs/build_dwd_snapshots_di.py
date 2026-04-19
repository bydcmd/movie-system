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
    parser = argparse.ArgumentParser(
        description="Build DWD user and movie snapshots from PostgreSQL ODS snapshots."
    )
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
        "If omitted, use the latest available dt partition not newer than calc-date.",
    )
    parser.add_argument(
        "--snapshots",
        default="user,movie",
        help="Comma-separated list of snapshots to build. Options: user, movie, all. Default: user,movie",
    )
    return parser.parse_args()


def load_partition(spark, table_name: str, partition_date: str) -> DataFrame:
    return spark.table(table_name).where(F.col("dt") == partition_date)


def deduplicate_users(users_df: DataFrame) -> DataFrame:
    rank_window = Window.partitionBy("user_id").orderBy(
        F.col("update_time").desc_nulls_last(),
        F.col("create_time").desc_nulls_last(),
        F.col("user_nickname").asc_nulls_last(),
    )
    return (
        users_df.where(F.col("user_id").isNotNull())
        .withColumn("rn", F.row_number().over(rank_window))
        .where(F.col("rn") == 1)
        .drop("rn")
    )


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


def build_user_snapshot(users_df: DataFrame, source_snapshot_dt: str) -> DataFrame:
    """Build user snapshot with analytics-safe profile fields only; raw passwords stay in ODS."""
    return users_df.select(
        F.col("user_id").cast("string").alias("user_id"),
        F.col("user_nickname").alias("user_nickname"),
        F.col("user_avatar").alias("user_avatar"),
        F.col("user_url").alias("user_url"),
        F.col("role").cast("tinyint").alias("user_role"),
        F.col("status").cast("int").alias("user_status"),
        F.col("password_version").cast("int").alias("password_version"),
        F.col("email").alias("email"),
        F.col("create_time").alias("create_time"),
        F.col("update_time").alias("update_time"),
        F.to_date(F.col("create_time")).cast("string").alias("register_date"),
        F.to_date(F.col("update_time")).cast("string").alias("update_date"),
        F.lit(source_snapshot_dt).alias("source_snapshot_dt"),
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


def parse_snapshots_arg(snapshots_arg: str) -> set[str]:
    """Parse the --snapshots argument into a set of snapshot types."""
    snapshots = {s.strip().lower() for s in snapshots_arg.split(",") if s.strip()}
    if "all" in snapshots:
        return {"user", "movie"}
    valid = {"user", "movie"}
    invalid = snapshots - valid
    if invalid:
        raise ValueError(f"Invalid snapshot types: {invalid}. Valid options: user, movie, all")
    return snapshots


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    user_config: dict[str, Any] = config["dwd_user_snapshot"]
    movie_config: dict[str, Any] = config["dwd_movie_snapshot"]

    calc_date = args.calc_date
    requested_snapshot_date = args.snapshot_date.strip()
    snapshots_to_build = parse_snapshots_arg(args.snapshots)

    spark = build_spark_session("movie-dwd-snapshots-di", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS dwd")

        results = []

        # Build user snapshot
        if "user" in snapshots_to_build:
            user_snapshot_date = resolve_dt_partition_date(
                user_config["source_table"],
                requested_snapshot_date,
                spark,
                fallback_max_date=calc_date,
            )
            user_source_df = load_partition(spark, user_config["source_table"], user_snapshot_date)
            assert_non_empty_partition(user_source_df, user_config["source_table"], {"dt": user_snapshot_date})
            user_result_df = build_user_snapshot(deduplicate_users(user_source_df), user_snapshot_date)

            write_partition(
                user_result_df,
                user_config["target_table"],
                user_config["sink_path"],
                calc_date,
                spark,
            )
            results.append(f"user: source={user_config['source_table']}, target={user_config['target_table']}, source_snapshot_dt={user_snapshot_date}")

        # Build movie snapshot
        if "movie" in snapshots_to_build:
            movie_snapshot_date = resolve_dt_partition_date(
                movie_config["source_table"],
                requested_snapshot_date,
                spark,
                fallback_max_date=calc_date,
            )
            movie_source_df = load_partition(spark, movie_config["source_table"], movie_snapshot_date)
            assert_non_empty_partition(movie_source_df, movie_config["source_table"], {"dt": movie_snapshot_date})
            movie_result_df = build_movie_snapshot(deduplicate_movies(movie_source_df), movie_snapshot_date)

            write_partition(
                movie_result_df,
                movie_config["target_table"],
                movie_config["sink_path"],
                calc_date,
                spark,
            )
            results.append(f"movie: source={movie_config['source_table']}, target={movie_config['target_table']}, source_snapshot_dt={movie_snapshot_date}")

        print(f"DWD snapshots build finished. dt={calc_date}. " + "; ".join(results))
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
