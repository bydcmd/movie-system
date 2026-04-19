from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame, SparkSession

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Load PostgreSQL full snapshot into Hive ODS partitions.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--batch-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Partition date in format YYYY-MM-DD.",
    )
    parser.add_argument(
        "--tables",
        default="",
        help="Optional comma-separated source tables to run, e.g. public.movies,public.users",
    )
    return parser.parse_args()


def build_jdbc_reader(spark: SparkSession, pg_config: dict[str, Any], table_config: dict[str, Any]):
    table_or_query = table_config.get("source_query")
    if table_or_query:
        table_or_query = f"({table_or_query}) src"
    else:
        table_or_query = table_config["source_table"]

    reader = (
        spark.read.format("jdbc")
        .option("url", pg_config["jdbc_url"])
        .option("dbtable", table_or_query)
        .option("driver", pg_config.get("driver", "org.postgresql.Driver"))
        .option("user", pg_config["user"])
        .option("password", pg_config["password"])
        .option("fetchsize", str(pg_config.get("fetch_size", 10000)))
    )

    partition_column = table_config.get("partition_column")
    lower_bound = table_config.get("lower_bound")
    upper_bound = table_config.get("upper_bound")
    num_partitions = table_config.get("num_partitions")
    if partition_column and lower_bound is not None and upper_bound is not None and num_partitions:
        reader = (
            reader.option("partitionColumn", partition_column)
            .option("lowerBound", str(lower_bound))
            .option("upperBound", str(upper_bound))
            .option("numPartitions", str(num_partitions))
        )

    return reader


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config = config["spark"]
    pg_config = config["postgres"]

    spark = build_spark_session(spark_config.get("app_name_batch", "movie-postgres-to-hive-ods"), spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ods")

        table_filter = {item.strip() for item in args.tables.split(",") if item.strip()}
        table_configs: list[dict[str, Any]] = pg_config["tables"]
        if table_filter:
            table_configs = [item for item in table_configs if item.get("source_table") in table_filter]

        if not table_configs:
            raise ValueError("No table configs selected for execution.")

        print(f"Start PostgreSQL -> Hive ODS batch. batch_date={args.batch_date}, table_count={len(table_configs)}")

        for table_cfg in table_configs:
            source_table = table_cfg.get("source_table", "<query>")
            print(f"Reading source: {source_table}")
            reader = build_jdbc_reader(spark, pg_config, table_cfg)
            df = reader.load()
            print(f"Writing target partition for source={source_table}")
            write_partition(df, table_cfg.get("target_table"), table_cfg["sink_path"], args.batch_date, spark)
            print(f"Completed source={source_table}")

        print("PostgreSQL -> Hive ODS batch finished.")
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
