from __future__ import annotations

import argparse
import copy
import datetime as dt
import re
from typing import Any

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import assert_non_empty_partition
from utils.spark_factory import build_spark_session

DEFAULT_ADS_HOT_MOVIES_POSTGRES_SYNC_CONFIG: dict[str, Any] = {
    "source_table": "ads.ads_hot_movies",
    "target_table": "public.stats_hot_movies",
    "supported_period_types": ["DAILY", "WEEKLY", "MONTHLY", "TOTAL"],
    "batch_size": 1000,
}

_TABLE_NAME_RE = re.compile(r"^[a-zA-Z_]\w*(\.[a-zA-Z_]\w*)*$")


def ensure_non_empty_partition(
    df: DataFrame,
    table_name: str,
    partition_spec: dict[str, str],
    spark: SparkSession,
) -> None:
    try:
        assert_non_empty_partition(df, table_name, partition_spec, spark=spark)
    except TypeError as exc:
        if "unexpected keyword argument 'spark'" not in str(exc):
            raise
        assert_non_empty_partition(df, table_name, partition_spec)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sync ADS hot movie rankings from Hive to PostgreSQL.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    return parser.parse_args()


def merge_nested_dict(defaults: dict[str, Any], overrides: dict[str, Any]) -> dict[str, Any]:
    merged = copy.deepcopy(defaults)
    for key, value in overrides.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = merge_nested_dict(merged[key], value)
        else:
            merged[key] = value
    return merged


def validate_table_name(table_name: str) -> None:
    if not _TABLE_NAME_RE.match(table_name):
        raise ValueError(f"Invalid table name: {table_name!r}")


def normalize_period_types(period_types: list[Any] | tuple[Any, ...]) -> list[str]:
    normalized = [str(item).strip().upper() for item in period_types if str(item).strip()]
    if not normalized:
        raise ValueError("supported_period_types must not be empty")
    return normalized


def build_source_frame(
    spark: SparkSession,
    source_table: str,
    calc_date: str,
    supported_period_types: list[str],
) -> DataFrame:
    source_df = spark.table(source_table).where(F.col("dt") == calc_date)
    ensure_non_empty_partition(source_df, source_table, {"dt": calc_date}, spark=spark)

    available_period_types = [
        row["period_type"]
        for row in source_df.select(F.upper(F.trim(F.col("period_type"))).alias("period_type")).distinct().collect()
        if row["period_type"]
    ]

    filtered_df = (
        source_df.select(
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.upper(F.trim(F.col("period_type"))).alias("period_type"),
            F.col("hot_score").cast("double").alias("hot_score"),
        )
        .where(
            F.col("movie_id").isNotNull()
            & F.col("period_type").isNotNull()
            & F.col("hot_score").isNotNull()
            & F.col("period_type").isin(supported_period_types)
        )
        .withColumn("calc_date", F.lit(calc_date).cast("date"))
        .dropDuplicates(["movie_id", "period_type", "calc_date"])
        .select("movie_id", "period_type", "hot_score", "calc_date")
    )

    if filtered_df.limit(1).count() == 0:
        joined_available = ",".join(sorted(available_period_types)) if available_period_types else "<empty>"
        joined_supported = ",".join(supported_period_types)
        rebuild_hint = ""
        if available_period_types == ["SNAPSHOT"]:
            rebuild_hint = " Rebuild the ads_hot_movies partition with the updated build_ads_hot_movies.py first."
        raise ValueError(
            "No supported period data found in source partition. "
            f"source_table={source_table}, dt={calc_date}, available_period_types={joined_available}, "
            f"supported_period_types={joined_supported}.{rebuild_hint}"
        )

    return filtered_df


def delete_target_rows(
    spark: SparkSession,
    pg_config: dict[str, Any],
    target_table: str,
    calc_date: str,
) -> int:
    validate_table_name(target_table)

    jvm = spark.sparkContext._gateway.jvm
    driver = pg_config.get("driver", "org.postgresql.Driver")
    jvm.java.lang.Class.forName(driver)

    connection = None
    statement = None
    try:
        connection = jvm.java.sql.DriverManager.getConnection(
            pg_config["jdbc_url"],
            pg_config["user"],
            pg_config["password"],
        )
        connection.setAutoCommit(False)
        statement = connection.prepareStatement(f"DELETE FROM {target_table} WHERE calc_date = ?")
        statement.setDate(1, jvm.java.sql.Date.valueOf(calc_date))
        deleted_rows = int(statement.executeUpdate())
        connection.commit()
        return deleted_rows
    except Exception:
        if connection is not None:
            connection.rollback()
        raise
    finally:
        if statement is not None:
            statement.close()
        if connection is not None:
            connection.close()


def write_to_postgres(
    df: DataFrame,
    pg_config: dict[str, Any],
    target_table: str,
    batch_size: int,
) -> None:
    validate_table_name(target_table)

    (
        df.write.format("jdbc")
        .option("url", pg_config["jdbc_url"])
        .option("dbtable", target_table)
        .option("driver", pg_config.get("driver", "org.postgresql.Driver"))
        .option("user", pg_config["user"])
        .option("password", pg_config["password"])
        .option("batchsize", str(batch_size))
        .mode("append")
        .save()
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    pg_config: dict[str, Any] = config["postgres"]
    sync_config = merge_nested_dict(
        DEFAULT_ADS_HOT_MOVIES_POSTGRES_SYNC_CONFIG,
        config.get("ads_hot_movies_postgres_sync", {}),
    )

    source_table = str(sync_config["source_table"]).strip()
    target_table = str(sync_config["target_table"]).strip()
    supported_period_types = normalize_period_types(sync_config.get("supported_period_types", []))
    batch_size = int(sync_config.get("batch_size", 1000))
    if batch_size <= 0:
        raise ValueError(f"Invalid batch_size: {batch_size}")

    spark = build_spark_session("movie-ads-hot-movies-to-postgres", spark_config)
    try:
        result_df = build_source_frame(
            spark=spark,
            source_table=source_table,
            calc_date=args.calc_date,
            supported_period_types=supported_period_types,
        ).cache()
        try:
            row_count = result_df.count()
            deleted_rows = delete_target_rows(
                spark=spark,
                pg_config=pg_config,
                target_table=target_table,
                calc_date=args.calc_date,
            )
            write_to_postgres(
                df=result_df,
                pg_config=pg_config,
                target_table=target_table,
                batch_size=batch_size,
            )
            print(
                "ADS hot movie sync to PostgreSQL finished. "
                f"source={source_table}, target={target_table}, dt={args.calc_date}, "
                f"rows={row_count}, deleted_existing_rows={deleted_rows}"
            )
        finally:
            result_df.unpersist()
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
