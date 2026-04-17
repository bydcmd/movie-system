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
    parser = argparse.ArgumentParser(description="Build ADS user retention metrics from DWD event wide table.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    return parser.parse_args()


def parse_retention_days(values: list[Any] | None) -> list[int]:
    defaults = [1, 7, 30]
    raw_values = values if values else defaults

    resolved = sorted({int(v) for v in raw_values})
    if not resolved or resolved[0] <= 0:
        raise ValueError(f"Invalid retention_days: {raw_values}")
    return resolved


def build_retention(
    register_events_df: DataFrame,
    active_events_df: DataFrame,
    calc_date: str,
    retention_days: list[int],
) -> DataFrame:
    register_users_df = (
        register_events_df.where(
            (F.col("is_register") == 1) & F.col("user_id").isNotNull() & (F.col("dt") <= F.lit(calc_date))
        )
        .groupBy("user_id")
        .agg(F.min("dt").alias("cohort_dt"))
    )

    active_users_df = (
        active_events_df.where(F.col("user_id").isNotNull() & (F.col("dt") <= F.lit(calc_date)))
        .select("user_id", "dt")
        .dropDuplicates()
    )

    retained_df = (
        register_users_df.join(active_users_df, on="user_id", how="inner")
        .withColumn("retention_day", F.datediff(F.to_date(F.col("dt")), F.to_date(F.col("cohort_dt"))))
        .where(F.col("retention_day").isin(*retention_days))
        .groupBy("cohort_dt", "retention_day")
        .agg(F.countDistinct("user_id").cast("bigint").alias("retained_users"))
    )

    cohort_df = register_users_df.groupBy("cohort_dt").agg(F.countDistinct("user_id").cast("bigint").alias("cohort_users"))
    retention_days_df = register_users_df.sparkSession.createDataFrame([(d,) for d in retention_days], ["retention_day"])

    base_df = cohort_df.crossJoin(retention_days_df)

    return (
        base_df.join(retained_df, on=["cohort_dt", "retention_day"], how="left")
        .select(
            F.col("cohort_dt").cast("string").alias("cohort_dt"),
            F.col("retention_day").cast("int").alias("retention_day"),
            F.col("cohort_users").cast("bigint").alias("cohort_users"),
            F.coalesce(F.col("retained_users"), F.lit(0)).cast("bigint").alias("retained_users"),
            F.when(
                F.col("cohort_users") > 0,
                F.round(F.coalesce(F.col("retained_users"), F.lit(0)) / F.col("cohort_users"), 4),
            )
            .otherwise(F.lit(None))
            .cast("decimal(10,4)")
            .alias("retention_rate"),
        )
        .orderBy(F.col("cohort_dt").asc(), F.col("retention_day").asc())
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    ads_config: dict[str, Any] = config["ads_user_retention"]

    calc_date = args.calc_date
    register_source_table = ads_config["register_source_table"]
    # active users derived directly from register source (all events) — no pre-aggregated DWS table needed
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]
    retention_days = parse_retention_days(ads_config.get("retention_days"))

    spark = build_spark_session("movie-ads-user-retention", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        register_events_df = spark.table(register_source_table)

        result_df = build_retention(register_events_df, register_events_df, calc_date, retention_days)
        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(
            "ADS user retention build finished. "
            f"register_source={register_source_table}, target={target_table}, dt={calc_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
