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

DEFAULT_ADS_HOT_MOVIES_SYNC_CONFIG: dict[str, Any] = {
    "source_table": "ads.ads_hot_movies",
    "target_table": "public.stats_hot_movies",
    "supported_period_types": ["DAILY", "WEEKLY", "MONTHLY", "TOTAL"],
    "batch_size": 1000,
}

DEFAULT_ADS_SIMILAR_MOVIES_SYNC_CONFIG: dict[str, Any] = {
    "source_table": "ads.ads_itemcf_similar_movies",
    "target_table": "public.stats_similar_movies",
    "supported_similarity_types": [2],
    "batch_size": 1000,
}

DEFAULT_ADS_USER_RETENTION_SYNC_CONFIG: dict[str, Any] = {
    "source_table": "ads.ads_user_retention",
    "target_table": "public.stats_user_retention",
    "batch_size": 1000,
}

DEFAULT_ADS_GENRE_PREFERENCE_SYNC_CONFIG: dict[str, Any] = {
    "source_table": "ads.ads_genre_preference_1d",
    "target_table": "public.stats_genre_preference_1d",
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
    parser = argparse.ArgumentParser(
        description="Sync ADS data from Hive to PostgreSQL."
    )
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument(
        "--sync-types",
        default="all",
        help="Comma-separated list of sync types. Options: hot_movies, similar_movies, user_retention, genre_preference, all. Default: all",
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


def normalize_similarity_types(similarity_types: list[Any] | tuple[Any, ...]) -> list[int]:
    normalized: list[int] = []
    for item in similarity_types:
        if item is None:
            continue
        normalized.append(int(item))

    deduplicated = list(dict.fromkeys(normalized))
    if not deduplicated:
        raise ValueError("supported_similarity_types must not be empty")
    return deduplicated


def parse_sync_types_arg(sync_types_arg: str) -> set[str]:
    """Parse the --sync-types argument into a set of sync types."""
    sync_types = {s.strip().lower() for s in sync_types_arg.split(",") if s.strip()}
    if "all" in sync_types:
        return {
            "hot_movies",
            "similar_movies",
            "user_retention",
            "genre_preference",
        }
    valid = {
        "hot_movies",
        "similar_movies",
        "user_retention",
        "genre_preference",
    }
    invalid = sync_types - valid
    if invalid:
        raise ValueError(
            f"Invalid sync types: {invalid}. Valid options: hot_movies, similar_movies, user_retention, genre_preference, all"
        )
    return sync_types


def build_hot_movies_source_frame(
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


def build_similar_movies_source_frame(
    spark: SparkSession,
    source_table: str,
    calc_date: str,
    supported_similarity_types: list[int],
) -> DataFrame:
    source_df = spark.table(source_table).where(F.col("dt") == calc_date)
    ensure_non_empty_partition(source_df, source_table, {"dt": calc_date}, spark=spark)

    available_similarity_types = [
        int(row["similarity_type"])
        for row in source_df.select(F.col("similarity_type").cast("int").alias("similarity_type")).distinct().collect()
        if row["similarity_type"] is not None
    ]

    filtered_df = (
        source_df.select(
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("similar_movie_id").cast("bigint").alias("similar_movie_id"),
            F.col("similarity_score").cast("double").alias("similarity_score"),
            F.col("similarity_type").cast("smallint").alias("similarity_type"),
        )
        .where(
            F.col("movie_id").isNotNull()
            & F.col("similar_movie_id").isNotNull()
            & F.col("similarity_score").isNotNull()
            & F.col("similarity_type").isNotNull()
            & (F.col("movie_id") != F.col("similar_movie_id"))
            & F.col("similarity_type").isin([int(item) for item in supported_similarity_types])
        )
        .dropDuplicates(["movie_id", "similar_movie_id", "similarity_type"])
        .select("movie_id", "similar_movie_id", "similarity_score", "similarity_type")
    )

    if filtered_df.limit(1).count() == 0:
        joined_available = (
            ",".join(str(item) for item in sorted(available_similarity_types))
            if available_similarity_types
            else "<empty>"
        )
        joined_supported = ",".join(str(item) for item in supported_similarity_types)
        raise ValueError(
            "No supported similarity data found in source partition. "
            f"source_table={source_table}, dt={calc_date}, available_similarity_types={joined_available}, "
            f"supported_similarity_types={joined_supported}"
        )

    return filtered_df


def build_user_retention_source_frame(
    spark: SparkSession,
    source_table: str,
    calc_date: str,
) -> DataFrame:
    source_df = spark.table(source_table).where(F.col("dt") == calc_date)
    ensure_non_empty_partition(source_df, source_table, {"dt": calc_date}, spark=spark)

    filtered_df = source_df.select(
        F.col("cohort_dt").cast("string").alias("cohort_dt"),
        F.col("retention_day").cast("int").alias("retention_day"),
        F.col("cohort_users").cast("bigint").alias("cohort_users"),
        F.col("retained_users").cast("bigint").alias("retained_users"),
        F.col("retention_rate").cast("decimal(10,4)").alias("retention_rate"),
    ).withColumn("calc_date", F.lit(calc_date).cast("date"))

    return filtered_df


def build_genre_preference_source_frame(
    spark: SparkSession,
    source_table: str,
    calc_date: str,
) -> DataFrame:
    source_df = spark.table(source_table).where(F.col("dt") == calc_date)
    ensure_non_empty_partition(source_df, source_table, {"dt": calc_date}, spark=spark)

    filtered_df = source_df.select(
        F.col("genre").alias("genre"),
        F.col("rank_no").cast("int").alias("rank_no"),
        F.col("movie_cnt").cast("bigint").alias("movie_cnt"),
        F.col("view_pv").cast("bigint").alias("view_pv"),
        F.col("view_uv").cast("bigint").alias("view_uv"),
        F.col("rating_cnt").cast("bigint").alias("rating_cnt"),
        F.col("watched_cnt").cast("bigint").alias("watched_cnt"),
        F.col("hot_score_sum").cast("decimal(18,4)").alias("hot_score_sum"),
    ).withColumn("calc_date", F.lit(calc_date).cast("date"))

    return filtered_df


def delete_hot_movies_target_rows(
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


def delete_similar_movies_target_rows(
    spark: SparkSession,
    pg_config: dict[str, Any],
    target_table: str,
    similarity_types: list[int],
) -> int:
    validate_table_name(target_table)
    if not similarity_types:
        return 0

    jvm = spark.sparkContext._gateway.jvm
    driver = pg_config.get("driver", "org.postgresql.Driver")
    jvm.java.lang.Class.forName(driver)

    placeholders = ",".join("?" for _ in similarity_types)
    connection = None
    statement = None
    try:
        connection = jvm.java.sql.DriverManager.getConnection(
            pg_config["jdbc_url"],
            pg_config["user"],
            pg_config["password"],
        )
        connection.setAutoCommit(False)
        statement = connection.prepareStatement(
            f"DELETE FROM {target_table} WHERE similarity_type IN ({placeholders})"
        )
        for index, similarity_type in enumerate(similarity_types, start=1):
            statement.setInt(index, int(similarity_type))
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


def delete_by_calc_date(
    spark: SparkSession,
    pg_config: dict[str, Any],
    target_table: str,
    calc_date: str,
) -> int:
    """Generic delete by calc_date for simple stats tables."""
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


def sync_hot_movies(
    spark: SparkSession,
    pg_config: dict[str, Any],
    sync_config: dict[str, Any],
    calc_date: str,
) -> str:
    """Sync hot movies from ADS to PostgreSQL. Returns result message."""
    source_table = str(sync_config["source_table"]).strip()
    target_table = str(sync_config["target_table"]).strip()
    supported_period_types = normalize_period_types(sync_config.get("supported_period_types", []))
    batch_size = int(sync_config.get("batch_size", 1000))
    if batch_size <= 0:
        raise ValueError(f"Invalid batch_size: {batch_size}")

    result_df = build_hot_movies_source_frame(
        spark=spark,
        source_table=source_table,
        calc_date=calc_date,
        supported_period_types=supported_period_types,
    ).cache()
    try:
        row_count = result_df.count()
        deleted_rows = delete_hot_movies_target_rows(
            spark=spark,
            pg_config=pg_config,
            target_table=target_table,
            calc_date=calc_date,
        )
        write_to_postgres(
            df=result_df,
            pg_config=pg_config,
            target_table=target_table,
            batch_size=batch_size,
        )
        return f"hot_movies: source={source_table}, target={target_table}, rows={row_count}, deleted={deleted_rows}"
    finally:
        result_df.unpersist()


def sync_similar_movies(
    spark: SparkSession,
    pg_config: dict[str, Any],
    sync_config: dict[str, Any],
    calc_date: str,
) -> str:
    """Sync similar movies from ADS to PostgreSQL. Returns result message."""
    source_table = str(sync_config["source_table"]).strip()
    target_table = str(sync_config["target_table"]).strip()
    supported_similarity_types = normalize_similarity_types(sync_config.get("supported_similarity_types", []))
    batch_size = int(sync_config.get("batch_size", 1000))
    if batch_size <= 0:
        raise ValueError(f"Invalid batch_size: {batch_size}")

    result_df = build_similar_movies_source_frame(
        spark=spark,
        source_table=source_table,
        calc_date=calc_date,
        supported_similarity_types=supported_similarity_types,
    ).cache()
    try:
        row_count = result_df.count()
        deleted_rows = delete_similar_movies_target_rows(
            spark=spark,
            pg_config=pg_config,
            target_table=target_table,
            similarity_types=supported_similarity_types,
        )
        write_to_postgres(
            df=result_df,
            pg_config=pg_config,
            target_table=target_table,
            batch_size=batch_size,
        )
        return f"similar_movies: source={source_table}, target={target_table}, similarity_types={supported_similarity_types}, rows={row_count}, deleted={deleted_rows}"
    finally:
        result_df.unpersist()


def sync_user_retention(
    spark: SparkSession,
    pg_config: dict[str, Any],
    sync_config: dict[str, Any],
    calc_date: str,
) -> str:
    """Sync user retention from ADS to PostgreSQL. Returns result message."""
    source_table = str(sync_config["source_table"]).strip()
    target_table = str(sync_config["target_table"]).strip()
    batch_size = int(sync_config.get("batch_size", 1000))
    if batch_size <= 0:
        raise ValueError(f"Invalid batch_size: {batch_size}")

    result_df = build_user_retention_source_frame(
        spark=spark,
        source_table=source_table,
        calc_date=calc_date,
    ).cache()
    try:
        row_count = result_df.count()
        deleted_rows = delete_by_calc_date(
            spark=spark,
            pg_config=pg_config,
            target_table=target_table,
            calc_date=calc_date,
        )
        write_to_postgres(
            df=result_df,
            pg_config=pg_config,
            target_table=target_table,
            batch_size=batch_size,
        )
        return f"user_retention: source={source_table}, target={target_table}, rows={row_count}, deleted={deleted_rows}"
    finally:
        result_df.unpersist()


def sync_genre_preference(
    spark: SparkSession,
    pg_config: dict[str, Any],
    sync_config: dict[str, Any],
    calc_date: str,
) -> str:
    """Sync genre preference from ADS to PostgreSQL. Returns result message."""
    source_table = str(sync_config["source_table"]).strip()
    target_table = str(sync_config["target_table"]).strip()
    batch_size = int(sync_config.get("batch_size", 1000))
    if batch_size <= 0:
        raise ValueError(f"Invalid batch_size: {batch_size}")

    result_df = build_genre_preference_source_frame(
        spark=spark,
        source_table=source_table,
        calc_date=calc_date,
    ).cache()
    try:
        row_count = result_df.count()
        deleted_rows = delete_by_calc_date(
            spark=spark,
            pg_config=pg_config,
            target_table=target_table,
            calc_date=calc_date,
        )
        write_to_postgres(
            df=result_df,
            pg_config=pg_config,
            target_table=target_table,
            batch_size=batch_size,
        )
        return f"genre_preference: source={source_table}, target={target_table}, rows={row_count}, deleted={deleted_rows}"
    finally:
        result_df.unpersist()


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    pg_config: dict[str, Any] = config["postgres"]

    hot_movies_sync_config = merge_nested_dict(
        DEFAULT_ADS_HOT_MOVIES_SYNC_CONFIG,
        config.get("ads_hot_movies_postgres_sync", {}),
    )
    similar_movies_sync_config = merge_nested_dict(
        DEFAULT_ADS_SIMILAR_MOVIES_SYNC_CONFIG,
        config.get("ads_itemcf_similar_movies_postgres_sync", {}),
    )
    user_retention_sync_config = merge_nested_dict(
        DEFAULT_ADS_USER_RETENTION_SYNC_CONFIG,
        config.get("ads_user_retention_postgres_sync", {}),
    )
    genre_preference_sync_config = merge_nested_dict(
        DEFAULT_ADS_GENRE_PREFERENCE_SYNC_CONFIG,
        config.get("ads_genre_preference_postgres_sync", {}),
    )
    sync_types = parse_sync_types_arg(args.sync_types)

    spark = build_spark_session("movie-ads-to-postgres-sync", spark_config)
    try:
        results = []

        if "hot_movies" in sync_types:
            result = sync_hot_movies(
                spark=spark,
                pg_config=pg_config,
                sync_config=hot_movies_sync_config,
                calc_date=args.calc_date,
            )
            results.append(result)

        if "similar_movies" in sync_types:
            result = sync_similar_movies(
                spark=spark,
                pg_config=pg_config,
                sync_config=similar_movies_sync_config,
                calc_date=args.calc_date,
            )
            results.append(result)

        if "user_retention" in sync_types:
            result = sync_user_retention(
                spark=spark,
                pg_config=pg_config,
                sync_config=user_retention_sync_config,
                calc_date=args.calc_date,
            )
            results.append(result)

        if "genre_preference" in sync_types:
            result = sync_genre_preference(
                spark=spark,
                pg_config=pg_config,
                sync_config=genre_preference_sync_config,
                calc_date=args.calc_date,
            )
            results.append(result)

        print(f"ADS to PostgreSQL sync finished. dt={args.calc_date}. " + "; ".join(results))
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
