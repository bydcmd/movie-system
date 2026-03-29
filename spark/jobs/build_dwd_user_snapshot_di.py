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
    parser = argparse.ArgumentParser(description="Build dwd_user_snapshot_di from PostgreSQL ODS snapshot.")
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


def build_user_snapshot(users_df: DataFrame, source_snapshot_dt: str) -> DataFrame:
    # Keep analytics-safe profile fields only; raw passwords stay in ODS.
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


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    snapshot_config: dict[str, Any] = config["dwd_user_snapshot"]

    calc_date = args.calc_date
    requested_snapshot_date = args.snapshot_date.strip()

    spark = build_spark_session("movie-dwd-user-snapshot-di", spark_config)
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
        result_df = build_user_snapshot(deduplicate_users(source_df), snapshot_date)

        write_partition(
            result_df,
            snapshot_config["target_table"],
            snapshot_config["sink_path"],
            calc_date,
            spark,
        )

        print(
            "DWD user snapshot build finished. "
            f"source={snapshot_config['source_table']}, target={snapshot_config['target_table']}, "
            f"dt={calc_date}, source_snapshot_dt={snapshot_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
