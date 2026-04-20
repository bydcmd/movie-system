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


def resolve_metric_window(calc_date: str, metric_period_type: str) -> tuple[str | None, str]:
    normalized_period_type = metric_period_type.strip().upper()
    if normalized_period_type == "TOTAL":
        return None, calc_date

    period_days = {"DAILY": 1, "WEEKLY": 7, "MONTHLY": 30}.get(normalized_period_type, 1)
    start_date = (dt.date.fromisoformat(calc_date) - dt.timedelta(days=period_days - 1)).isoformat()
    return start_date, calc_date


def build_genre_movie_mapping(event_source_df: DataFrame) -> DataFrame:
    return (
        event_source_df.select(
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.explode(F.split(F.coalesce(F.col("movie_genres"), F.lit("")), "[,/]")).alias("genre"),
        )
        .withColumn("genre", F.trim(F.col("genre")))
        .where(F.col("movie_id").isNotNull() & (F.col("genre") != ""))
        .dropDuplicates(["movie_id", "genre"])
    )


def build_true_genre_view_uv(
    genre_movie_df: DataFrame,
    event_source_df: DataFrame,
    calc_date: str,
    metric_period_type: str,
) -> DataFrame:
    window_start, window_end = resolve_metric_window(calc_date, metric_period_type)

    view_events_df = (
        event_source_df.where(
            (F.col("is_view") == 1)
            & F.col("user_id").isNotNull()
            & F.col("movie_id").isNotNull()
            & F.col("event_ts").isNotNull()
        )
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.to_date(F.col("event_ts")).alias("event_date"),
            F.to_date(F.col("dt")).alias("dt_date"),
        )
    )

    end_date = F.lit(window_end).cast("date")
    if window_start:
        start_date = F.lit(window_start).cast("date")
        view_events_df = view_events_df.where(
            (F.col("event_date") >= start_date)
            & (F.col("event_date") <= end_date)
            & (F.col("dt_date") >= start_date)
            & (F.col("dt_date") <= end_date)
        )
    else:
        view_events_df = view_events_df.where(
            (F.col("event_date") <= end_date) & (F.col("dt_date") <= end_date)
        )

    return (
        genre_movie_df.join(view_events_df, on="movie_id", how="inner")
        .groupBy("genre")
        .agg(F.countDistinct("user_id").cast("bigint").alias("view_uv"))
    )


def build_genre_movie_metrics(
    movie_metrics_df: DataFrame,
    genre_movie_df: DataFrame,
    genre_view_uv_df: DataFrame,
) -> DataFrame:
    genre_metrics_df = (
        movie_metrics_df.select(
            F.col("movie_id").cast("bigint").alias("movie_id"),
            "view_pv",
            "rating_cnt",
            "watched_cnt",
            F.col("hot_score").cast("double").alias("hot_score_value"),
        )
        .join(genre_movie_df, on="movie_id", how="inner")
        .groupBy("genre")
        .agg(
            F.countDistinct("movie_id").cast("bigint").alias("movie_cnt"),
            F.sum("view_pv").cast("bigint").alias("view_pv"),
            F.sum("rating_cnt").cast("bigint").alias("rating_cnt"),
            F.sum("watched_cnt").cast("bigint").alias("watched_cnt"),
            F.round(F.sum("hot_score_value"), 4).cast("decimal(18,4)").alias("hot_score_sum"),
        )
    )

    return (
        genre_metrics_df.alias("gm")
        .join(genre_view_uv_df.alias("gu"), on="genre", how="left")
        .select(
            F.col("genre"),
            F.col("gm.movie_cnt").alias("movie_cnt"),
            F.col("gm.view_pv").alias("view_pv"),
            F.coalesce(F.col("gu.view_uv"), F.lit(0)).cast("bigint").alias("view_uv"),
            F.col("gm.rating_cnt").alias("rating_cnt"),
            F.col("gm.watched_cnt").alias("watched_cnt"),
            F.col("gm.hot_score_sum").alias("hot_score_sum"),
        )
    )


def build_genre_preference(
    movie_metrics_df: DataFrame,
    genre_movie_df: DataFrame,
    genre_view_uv_df: DataFrame,
    top_n: int,
) -> DataFrame:
    genre_metrics_df = build_genre_movie_metrics(movie_metrics_df, genre_movie_df, genre_view_uv_df)

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
    event_source_table = dm_config.get("event_source_table", "dw.dw_user_event_fact_di")
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

        event_source_df = spark.table(event_source_table)
        genre_movie_df = build_genre_movie_mapping(event_source_df)
        genre_view_uv_df = build_true_genre_view_uv(genre_movie_df, event_source_df, calc_date, metric_period_type)
        result_df = build_genre_preference(movie_metrics_df, genre_movie_df, genre_view_uv_df, top_n)
        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(
            "DM genre preference build finished. "
            f"source={source_table}, event_source={event_source_table}, target={target_table}, dt={calc_date}, top_n={top_n}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
