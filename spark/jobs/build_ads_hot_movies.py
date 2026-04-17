from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import Window
from pyspark.sql.column import Column
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401
from build_dws_postgres_interactions_1d import (
    DEFAULT_DWS_POSTGRES_INTERACTIONS_CONFIG,
    aggregate_movie_comment_like_metrics,
    build_movie_comment_like_metrics,
    build_movie_engagement,
    build_user_movie_interactions,
    clean_favorites_by_existing_movies,
)

from utils.config_loader import load_config
from utils.hive_utils import assert_non_empty_partition, resolve_common_dt_partition_date, write_partition
from utils.spark_factory import build_spark_session

DEFAULT_PERIOD_DAYS: dict[str, int] = {"DAILY": 1, "WEEKLY": 7, "MONTHLY": 30}
TOTAL_PERIOD_TYPE = "TOTAL"
DEFAULT_TOTAL_SOURCE_TABLE = "dws.dws_movie_engagement_1d"
SNAPSHOT_SOURCE_TYPE = "movie_metric_snapshot"
DEFAULT_RAW_SOURCE_TABLES: dict[str, str] = {
    key: str(value)
    for key, value in DEFAULT_DWS_POSTGRES_INTERACTIONS_CONFIG["source_tables"].items()
}


def ensure_non_empty_partition(
    df: DataFrame,
    table_name: str,
    partition_spec: dict[str, str],
    spark,
) -> None:
    try:
        assert_non_empty_partition(df, table_name, partition_spec, spark=spark)
    except TypeError as exc:
        if "unexpected keyword argument 'spark'" not in str(exc):
            raise
        assert_non_empty_partition(df, table_name, partition_spec)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build ADS hot movie rankings from DWS daily aggregates.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument("--top-n", type=int, default=0, help="Override top N rows per period. 0 means use config.")
    return parser.parse_args()


def resolve_period_days(period_days: dict[str, Any] | None) -> dict[str, int | None]:
    defaults: dict[str, int | None] = {
        **DEFAULT_PERIOD_DAYS,
        TOTAL_PERIOD_TYPE: None,
    }
    if not period_days:
        return defaults

    resolved: dict[str, int | None] = {}
    for period, default_days in defaults.items():
        raw_value = period_days.get(period, default_days)
        if raw_value is None:
            if period == TOTAL_PERIOD_TYPE:
                resolved[period] = None
                continue
            raise ValueError(f"Missing period days for {period}")
        value = int(raw_value)
        if value <= 0:
            raise ValueError(f"Invalid period days for {period}: {raw_value}")
        resolved[period] = value
    return resolved


def build_hot_score_expr(weights: dict[str, Any]) -> Column:
    return (
        F.col("view_pv") * F.lit(float(weights.get("view_pv", 1.0)))
        + F.col("view_uv") * F.lit(float(weights.get("view_uv", 1.5)))
        + F.col("rating_cnt") * F.lit(float(weights.get("rating_cnt", 2.0)))
        + F.col("comment_cnt") * F.lit(float(weights.get("comment_cnt", 2.0)))
        + F.col("comment_like_cnt") * F.lit(float(weights.get("comment_like_cnt", 0.8)))
        + F.col("favorite_add_cnt") * F.lit(float(weights.get("favorite_add_cnt", 1.2)))
        + F.col("favorite_remove_cnt") * F.lit(float(weights.get("favorite_remove_cnt", -0.8)))
        + F.col("watched_cnt") * F.lit(float(weights.get("watched_cnt", 2.5)))
        + F.col("active_user_cnt") * F.lit(float(weights.get("active_user_cnt", 1.0)))
    )


def filter_rankable_movies(df: DataFrame) -> DataFrame:
    return df.where(
        F.col("movie_id").isNotNull()
        & F.col("movie_name").isNotNull()
        & (F.length(F.trim(F.col("movie_name"))) > 0)
    )


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


def filter_events_by_date_range(
    events_df: DataFrame,
    timestamp_column: str,
    start_date: str,
    end_date: str,
) -> DataFrame:
    return events_df.where(
        (F.to_date(F.col(timestamp_column)) >= F.lit(start_date).cast("date"))
        & (F.to_date(F.col(timestamp_column)) <= F.lit(end_date).cast("date"))
    )


def load_raw_hot_sources(
    spark,
    raw_source_tables: dict[str, str],
    calc_date: str,
) -> tuple[str, DataFrame, DataFrame, DataFrame]:
    snapshot_date = resolve_common_dt_partition_date(
        [
            raw_source_tables["movie_snapshot"],
            raw_source_tables["ratings"],
            raw_source_tables["comments"],
            raw_source_tables["comment_likes"],
            raw_source_tables["favorites"],
            raw_source_tables["view_history"],
            raw_source_tables["watched_movies"],
        ],
        requested_date="",
        spark=spark,
        fallback_max_date=calc_date,
    )

    movie_snapshot_df = load_partition(spark, raw_source_tables["movie_snapshot"], snapshot_date)
    ensure_non_empty_partition(movie_snapshot_df, raw_source_tables["movie_snapshot"], {"dt": snapshot_date}, spark=spark)

    ratings_df = load_partition(spark, raw_source_tables["ratings"], snapshot_date)
    comments_df = load_partition(spark, raw_source_tables["comments"], snapshot_date)
    comment_likes_df = load_partition(spark, raw_source_tables["comment_likes"], snapshot_date)
    favorites_df = load_partition(spark, raw_source_tables["favorites"], snapshot_date)
    view_history_df = load_partition(spark, raw_source_tables["view_history"], snapshot_date)
    watched_movies_df = load_partition(spark, raw_source_tables["watched_movies"], snapshot_date)

    cleaned_favorites_df = clean_favorites_by_existing_movies(favorites_df, movie_snapshot_df)
    interactions_df = build_user_movie_interactions(
        view_history_df=view_history_df,
        ratings_df=ratings_df,
        comments_df=comments_df,
        favorites_df=cleaned_favorites_df,
        watched_movies_df=watched_movies_df,
    )
    comment_like_events_df = build_movie_comment_like_metrics(comment_likes_df, comments_df)
    return snapshot_date, movie_snapshot_df, interactions_df, comment_like_events_df


def build_raw_period_hot_ranking(
    interactions_df: DataFrame,
    comment_like_events_df: DataFrame,
    movie_snapshot_df: DataFrame,
    calc_date: str,
    period_type: str,
    period_days: int,
    top_n: int,
    weights: dict[str, Any],
) -> DataFrame:
    calc_date_obj = dt.date.fromisoformat(calc_date)
    start_date = (calc_date_obj - dt.timedelta(days=period_days - 1)).isoformat()

    period_interactions_df = filter_events_by_date_range(interactions_df, "event_ts", start_date, calc_date)
    period_comment_like_events_df = filter_events_by_date_range(comment_like_events_df, "event_ts", start_date, calc_date)
    period_movie_comment_likes_df = aggregate_movie_comment_like_metrics(period_comment_like_events_df)

    aggregated_df = (
        build_movie_engagement(
            interactions_df=period_interactions_df,
            movie_snapshot_df=movie_snapshot_df,
            movie_comment_likes_df=period_movie_comment_likes_df,
            weights=weights,
        )
        .withColumn("period_type", F.lit(period_type))
        .withColumn("window_start", F.lit(start_date).cast("string"))
        .withColumn("window_end", F.lit(calc_date).cast("string"))
    )
    aggregated_df = filter_rankable_movies(aggregated_df)

    rank_window = Window.partitionBy("period_type").orderBy(
        F.col("hot_score").desc(),
        F.col("view_pv").desc(),
        F.col("movie_id").asc(),
    )

    ranked_df = aggregated_df.withColumn("rank_no", F.row_number().over(rank_window))
    return ranked_df.where(F.col("rank_no") <= top_n).select(
        "movie_id",
        "movie_name",
        "movie_year",
        "movie_genres",
        "movie_score",
        "movie_douban_score",
        "period_type",
        "rank_no",
        "hot_score",
        "view_pv",
        "view_uv",
        "rating_cnt",
        "rating_avg",
        "comment_cnt",
        "comment_like_cnt",
        "favorite_add_cnt",
        "favorite_remove_cnt",
        "watched_cnt",
        "active_user_cnt",
        "window_start",
        "window_end",
        "last_event_ts",
    )


def build_period_hot_ranking(
    source_df: DataFrame,
    calc_date: str,
    period_type: str,
    period_days: int | None,
    top_n: int,
    weights: dict[str, Any],
) -> DataFrame:
    if period_days is None:
        start_date = None
        filtered_df = source_df.where(F.col("dt_date") <= F.lit(calc_date).cast("date"))
    else:
        calc_date_obj = dt.date.fromisoformat(calc_date)
        start_date_obj = calc_date_obj - dt.timedelta(days=period_days - 1)
        start_date = start_date_obj.isoformat()
        filtered_df = source_df.where(
            (F.col("dt_date") >= F.lit(start_date).cast("date")) & (F.col("dt_date") <= F.lit(calc_date).cast("date"))
        )

    rating_sum = (
        F.sum(F.coalesce(F.col("rating_avg").cast("double"), F.lit(0.0)) * F.coalesce(F.col("rating_cnt"), F.lit(0)))
        .cast("double")
        .alias("rating_sum")
    )

    aggregated_df = (
        filtered_df.groupBy("movie_id")
        .agg(
            F.max("movie_name").alias("movie_name"),
            F.max("movie_year").cast("int").alias("movie_year"),
            F.max("movie_genres").alias("movie_genres"),
            F.max("movie_score").cast("decimal(3,1)").alias("movie_score"),
            F.max("movie_douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
            F.sum("view_pv").cast("bigint").alias("view_pv"),
            F.sum("view_uv").cast("bigint").alias("view_uv"),
            F.sum("rating_cnt").cast("bigint").alias("rating_cnt"),
            rating_sum,
            F.sum("comment_cnt").cast("bigint").alias("comment_cnt"),
            F.sum("comment_like_cnt").cast("bigint").alias("comment_like_cnt"),
            F.sum("favorite_add_cnt").cast("bigint").alias("favorite_add_cnt"),
            F.sum("favorite_remove_cnt").cast("bigint").alias("favorite_remove_cnt"),
            F.sum("watched_cnt").cast("bigint").alias("watched_cnt"),
            F.sum("active_user_cnt").cast("bigint").alias("active_user_cnt"),
            F.max("last_event_ts").alias("last_event_ts"),
        )
        .withColumn(
            "rating_avg",
            F.when(F.col("rating_cnt") > 0, F.round(F.col("rating_sum") / F.col("rating_cnt"), 2))
            .otherwise(F.lit(None))
            .cast("decimal(10,2)"),
        )
        .drop("rating_sum")
        .withColumn("period_type", F.lit(period_type))
        .withColumn("window_start", F.lit(start_date).cast("string"))
        .withColumn("window_end", F.lit(calc_date).cast("string"))
        .withColumn("hot_score", F.round(build_hot_score_expr(weights), 4).cast("decimal(18,4)"))
    )
    aggregated_df = filter_rankable_movies(aggregated_df)

    rank_window = Window.partitionBy("period_type").orderBy(
        F.col("hot_score").desc(),
        F.col("view_pv").desc(),
        F.col("movie_id").asc(),
    )

    ranked_df = aggregated_df.withColumn("rank_no", F.row_number().over(rank_window))
    return ranked_df.where(F.col("rank_no") <= top_n).select(
        "movie_id",
        "movie_name",
        "movie_year",
        "movie_genres",
        "movie_score",
        "movie_douban_score",
        "period_type",
        "rank_no",
        "hot_score",
        "view_pv",
        "view_uv",
        "rating_cnt",
        "rating_avg",
        "comment_cnt",
        "comment_like_cnt",
        "favorite_add_cnt",
        "favorite_remove_cnt",
        "watched_cnt",
        "active_user_cnt",
        "window_start",
        "window_end",
        "last_event_ts",
    )


def build_snapshot_hot_ranking(
    source_df: DataFrame,
    calc_date: str,
    period_days: dict[str, int | None],
    top_n: int,
    weights: dict[str, Any],
) -> DataFrame:
    # Snapshot source keeps full-data engagement metrics as of calc_date rather than daily increments.
    # To stay compatible with downstream PostgreSQL stats tables, materialize the same ranking under
    # the configured period labels instead of emitting a custom SNAPSHOT period type.
    base_df = (
        source_df.select(
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("movie_name").alias("movie_name"),
            F.col("movie_year").cast("int").alias("movie_year"),
            F.col("movie_genres").alias("movie_genres"),
            F.col("movie_score").cast("decimal(3,1)").alias("movie_score"),
            F.col("movie_douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
            F.coalesce(F.col("view_pv"), F.lit(0)).cast("bigint").alias("view_pv"),
            F.coalesce(F.col("view_uv"), F.lit(0)).cast("bigint").alias("view_uv"),
            F.coalesce(F.col("rating_cnt"), F.lit(0)).cast("bigint").alias("rating_cnt"),
            F.col("rating_avg").cast("decimal(10,2)").alias("rating_avg"),
            F.coalesce(F.col("comment_cnt"), F.lit(0)).cast("bigint").alias("comment_cnt"),
            F.coalesce(F.col("comment_like_cnt"), F.lit(0)).cast("bigint").alias("comment_like_cnt"),
            F.coalesce(F.col("favorite_add_cnt"), F.lit(0)).cast("bigint").alias("favorite_add_cnt"),
            F.coalesce(F.col("favorite_remove_cnt"), F.lit(0)).cast("bigint").alias("favorite_remove_cnt"),
            F.coalesce(F.col("watched_cnt"), F.lit(0)).cast("bigint").alias("watched_cnt"),
            F.coalesce(F.col("active_user_cnt"), F.lit(0)).cast("bigint").alias("active_user_cnt"),
            F.col("last_event_ts").alias("last_event_ts"),
        )
        .withColumn("hot_score", F.round(build_hot_score_expr(weights), 4).cast("decimal(18,4)"))
    )
    base_df = filter_rankable_movies(base_df)

    rank_window = Window.orderBy(
        F.col("hot_score").desc(),
        F.col("view_pv").desc(),
        F.col("movie_id").asc(),
    )

    ranked_df = (
        base_df.withColumn("rank_no", F.row_number().over(rank_window))
        .where(F.col("rank_no") <= top_n)
        .select(
            "movie_id",
            "movie_name",
            "movie_year",
            "movie_genres",
            "movie_score",
            "movie_douban_score",
            "rank_no",
            "hot_score",
            "view_pv",
            "view_uv",
            "rating_cnt",
            "rating_avg",
            "comment_cnt",
            "comment_like_cnt",
            "favorite_add_cnt",
            "favorite_remove_cnt",
            "watched_cnt",
            "active_user_cnt",
            "last_event_ts",
        )
    )

    period_frames: list[DataFrame] = []
    calc_date_obj = dt.date.fromisoformat(calc_date)
    for period_type, days in period_days.items():
        window_start = None
        if days is not None:
            window_start = (calc_date_obj - dt.timedelta(days=days - 1)).isoformat()
        period_frames.append(
            ranked_df.select(
                "movie_id",
                "movie_name",
                "movie_year",
                "movie_genres",
                "movie_score",
                "movie_douban_score",
                F.lit(period_type).alias("period_type"),
                "rank_no",
                "hot_score",
                "view_pv",
                "view_uv",
                "rating_cnt",
                "rating_avg",
                "comment_cnt",
                "comment_like_cnt",
                "favorite_add_cnt",
                "favorite_remove_cnt",
                "watched_cnt",
                "active_user_cnt",
                F.lit(window_start).cast("string").alias("window_start"),
                F.lit(calc_date).cast("string").alias("window_end"),
                "last_event_ts",
            )
        )

    result_df = period_frames[0]
    for period_df in period_frames[1:]:
        result_df = result_df.unionByName(period_df)
    return result_df


def build_total_period_frame(
    spark,
    calc_date: str,
    total_source_table: str,
    total_source_type: str,
    top_n: int,
    weights: dict[str, Any],
) -> DataFrame:
    normalized_source_type = total_source_type.strip().lower()
    if normalized_source_type == SNAPSHOT_SOURCE_TYPE:
        total_source_df = spark.table(total_source_table).where(F.col("dt") == calc_date)
        ensure_non_empty_partition(total_source_df, total_source_table, {"dt": calc_date}, spark=spark)
        return build_snapshot_hot_ranking(
            source_df=total_source_df,
            calc_date=calc_date,
            period_days={TOTAL_PERIOD_TYPE: None},
            top_n=top_n,
            weights=weights,
        )

    total_source_df = (
        spark.table(total_source_table)
        .where(F.col("dt") <= calc_date)
        .withColumn("dt_date", F.to_date(F.col("dt")))
    )
    if total_source_df.limit(1).count() == 0:
        raise ValueError(
            "No source data found for ADS total hot movie build. "
            f"source={total_source_table}, source_type={normalized_source_type}, dt_max={calc_date}"
        )
    return build_period_hot_ranking(
        source_df=total_source_df,
        calc_date=calc_date,
        period_type=TOTAL_PERIOD_TYPE,
        period_days=None,
        top_n=top_n,
        weights=weights,
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    ads_config: dict[str, Any] = config["ads"]

    calc_date = args.calc_date
    source_table = ads_config["source_table"]
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]
    source_type = str(ads_config.get("source_type", "movie_metric_daily")).strip().lower()
    total_source_table = str(
        ads_config.get(
            "total_source_table",
            DEFAULT_TOTAL_SOURCE_TABLE if source_type == "movie_metric_daily" else source_table,
        )
    ).strip()
    total_source_type = str(ads_config.get("total_source_type", SNAPSHOT_SOURCE_TYPE if total_source_table != source_table else source_type)).strip().lower()

    top_n = args.top_n if args.top_n > 0 else int(ads_config.get("top_n", 100))
    if top_n <= 0:
        raise ValueError(f"Invalid top_n: {top_n}")

    period_days = resolve_period_days(ads_config.get("period_days"))
    weights = ads_config.get("hot_score_weights", {})

    spark = build_spark_session("movie-ads-hot-movies", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        if source_type == "movie_metric_snapshot":
            source_df = spark.table(source_table).where(F.col("dt") == calc_date)
            ensure_non_empty_partition(source_df, source_table, {"dt": calc_date}, spark=spark)
            result_df = build_snapshot_hot_ranking(
                source_df=source_df,
                calc_date=calc_date,
                period_days=period_days,
                top_n=top_n,
                weights=weights,
            )
        else:
            period_frames: list[DataFrame] = []
            bounded_period_days = {
                period_type: days
                for period_type, days in period_days.items()
                if period_type != TOTAL_PERIOD_TYPE and days is not None
            }
            if bounded_period_days:
                raw_source_tables = resolve_raw_source_tables(config, ads_config)
                _, movie_snapshot_df, interactions_df, comment_like_events_df = load_raw_hot_sources(
                    spark=spark,
                    raw_source_tables=raw_source_tables,
                    calc_date=calc_date,
                )
                interactions_df = interactions_df.cache()
                comment_like_events_df = comment_like_events_df.cache()
                try:
                    for period_type, days in bounded_period_days.items():
                        period_frames.append(
                            build_raw_period_hot_ranking(
                                interactions_df=interactions_df,
                                comment_like_events_df=comment_like_events_df,
                                movie_snapshot_df=movie_snapshot_df,
                                calc_date=calc_date,
                                period_type=period_type,
                                period_days=days,
                                top_n=top_n,
                                weights=weights,
                            )
                        )
                finally:
                    interactions_df.unpersist()
                    comment_like_events_df.unpersist()

            if TOTAL_PERIOD_TYPE in period_days:
                period_frames.append(
                    build_total_period_frame(
                        spark=spark,
                        calc_date=calc_date,
                        total_source_table=total_source_table,
                        total_source_type=total_source_type,
                        top_n=top_n,
                        weights=weights,
                    )
                )

            result_df = period_frames[0]
            for period_df in period_frames[1:]:
                result_df = result_df.unionByName(period_df)

        result_df = result_df.cache()
        try:
            if result_df.limit(1).count() == 0:
                raise ValueError(
                    "ADS hot movie build produced 0 rows; refusing to register an empty target partition. "
                    f"source={source_table}, source_type={source_type}, target={target_table}, dt={calc_date}"
                )

            write_partition(result_df, target_table, sink_path, calc_date, spark)
            print(
                "ADS hot ranking build finished. "
                f"source={source_table}, source_type={source_type}, target={target_table}, dt={calc_date}, top_n={top_n}"
            )
        finally:
            result_df.unpersist()
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
