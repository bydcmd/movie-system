from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import Window
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401
from build_dws_postgres_interactions_1d import DEFAULT_DWS_POSTGRES_INTERACTIONS_CONFIG

from utils.config_loader import load_config
from utils.hive_utils import assert_non_empty_partition, resolve_common_dt_partition_date, write_partition
from utils.spark_factory import build_spark_session

DEFAULT_RAW_SOURCE_TABLES: dict[str, str] = {
    "movie_snapshot": str(DEFAULT_DWS_POSTGRES_INTERACTIONS_CONFIG["source_tables"]["movie_snapshot"]),
    "view_history": str(DEFAULT_DWS_POSTGRES_INTERACTIONS_CONFIG["source_tables"]["view_history"]),
    "watched_movies": str(DEFAULT_DWS_POSTGRES_INTERACTIONS_CONFIG["source_tables"]["watched_movies"]),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build ADS daily genre preference ranking from DWS movie metrics.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument("--top-n", type=int, default=0, help="Override top N genres. 0 means use config.")
    return parser.parse_args()


def resolve_raw_source_tables(config: dict[str, Any], ads_config: dict[str, Any]) -> dict[str, str]:
    resolved = dict(DEFAULT_RAW_SOURCE_TABLES)

    dws_source_tables = config.get("dws_postgres_interactions", {}).get("source_tables", {})
    for key in resolved:
        if key in dws_source_tables:
            resolved[key] = str(dws_source_tables[key]).strip()

    ads_raw_source_tables = ads_config.get("raw_source_tables", {})
    for key in resolved:
        if key in ads_raw_source_tables:
            resolved[key] = str(ads_raw_source_tables[key]).strip()

    return resolved


def load_partition(spark, table_name: str, partition_date: str) -> DataFrame:
    return spark.table(table_name).where(F.col("dt") == partition_date)


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


def build_genre_user_metrics(
    movie_snapshot_df: DataFrame,
    view_history_df: DataFrame,
    watched_movies_df: DataFrame,
    calc_date: str,
) -> DataFrame:
    genre_mapping_df = (
        movie_snapshot_df.select(
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.explode(F.split(F.coalesce(F.col("movie_genres"), F.lit("")), "[,/]")).alias("genre"),
        )
        .withColumn("genre", F.trim(F.col("genre")))
        .where(F.col("genre") != "")
        .distinct()
    )

    genre_view_users_df = (
        view_history_df.where(
            F.col("user_id").isNotNull()
            & F.col("movie_id").isNotNull()
            & (F.to_date(F.col("view_time")) == F.lit(calc_date).cast("date"))
        )
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
        )
        .join(genre_mapping_df, on="movie_id", how="inner")
        .groupBy("genre")
        .agg(F.countDistinct("user_id").cast("bigint").alias("view_uv"))
    )

    genre_watched_users_df = (
        watched_movies_df.where(
            F.col("user_id").isNotNull()
            & F.col("movie_id").isNotNull()
            & (F.to_date(F.col("create_time")) == F.lit(calc_date).cast("date"))
        )
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
        )
        .join(genre_mapping_df, on="movie_id", how="inner")
        .groupBy("genre")
        .agg(F.countDistinct("user_id").cast("bigint").alias("watched_user_cnt"))
    )

    return genre_view_users_df.join(genre_watched_users_df, on="genre", how="full").select(
        F.col("genre").alias("genre"),
        F.coalesce(F.col("view_uv"), F.lit(0)).cast("bigint").alias("view_uv"),
        F.coalesce(F.col("watched_user_cnt"), F.lit(0)).cast("bigint").alias("watched_user_cnt"),
    )


def build_genre_preference(movie_metrics_df: DataFrame, genre_user_metrics_df: DataFrame, top_n: int) -> DataFrame:
    genre_metrics_df = (
        build_genre_movie_metrics(movie_metrics_df)
        .join(genre_user_metrics_df, on="genre", how="left")
        .withColumn("view_uv", F.coalesce(F.col("view_uv"), F.lit(0)).cast("bigint"))
        .withColumn("watched_user_cnt", F.coalesce(F.col("watched_user_cnt"), F.lit(0)).cast("bigint"))
        .withColumn(
            "watched_rate",
            F.when(F.col("view_uv") > 0, F.round(F.col("watched_user_cnt") / F.col("view_uv"), 4)).otherwise(F.lit(0)),
        )
    )

    rank_window = Window.orderBy(
        F.col("hot_score_sum").desc(), F.col("watched_rate").desc(), F.col("view_pv").desc(), F.col("genre").asc()
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
    ads_config: dict[str, Any] = config["ads_genre_preference"]

    calc_date = args.calc_date
    source_table = ads_config["source_table"]
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]

    top_n = args.top_n if args.top_n > 0 else int(ads_config.get("top_n", 100))
    if top_n <= 0:
        raise ValueError(f"Invalid top_n: {top_n}")

    spark = build_spark_session("movie-ads-genre-preference-1d", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        movie_metrics_df = spark.table(source_table).where(F.col("dt") == calc_date)
        raw_source_tables = resolve_raw_source_tables(config, ads_config)
        snapshot_date = resolve_common_dt_partition_date(
            [
                raw_source_tables["movie_snapshot"],
                raw_source_tables["view_history"],
                raw_source_tables["watched_movies"],
            ],
            requested_date="",
            spark=spark,
            fallback_max_date=calc_date,
        )
        movie_snapshot_df = load_partition(spark, raw_source_tables["movie_snapshot"], snapshot_date)
        assert_non_empty_partition(movie_snapshot_df, raw_source_tables["movie_snapshot"], {"dt": snapshot_date}, spark=spark)
        view_history_df = load_partition(spark, raw_source_tables["view_history"], snapshot_date)
        watched_movies_df = load_partition(spark, raw_source_tables["watched_movies"], snapshot_date)

        genre_user_metrics_df = build_genre_user_metrics(
            movie_snapshot_df=movie_snapshot_df,
            view_history_df=view_history_df,
            watched_movies_df=watched_movies_df,
            calc_date=calc_date,
        )
        result_df = build_genre_preference(movie_metrics_df, genre_user_metrics_df, top_n)
        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(f"ADS genre preference build finished. source={source_table}, target={target_table}, dt={calc_date}, top_n={top_n}")
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
