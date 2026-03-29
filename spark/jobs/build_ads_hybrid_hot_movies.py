from __future__ import annotations

import argparse
import copy
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import Window
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


DEFAULT_ADS_HYBRID_HOT_CONFIG: dict[str, Any] = {
    "base_source_table": "dws.dws_movie_engagement_1d",
    "recent_source_table": "dws.dws_movie_action_1d",
    "movie_profile_source_table": "dws.dws_movie_profile_1d",
    "target_table": "ads.ads_hybrid_hot_movies",
    "sink_path": "hdfs:///warehouse/movie/ads/hybrid_hot_movies",
    "period_days": {
        "DAILY": 1,
        "WEEKLY": 7,
        "MONTHLY": 30,
    },
    "top_n": 100,
    "rerank_weights": {
        "base_score": 1.0,
        "recent_trend": 0.35,
        "trend_ratio": 0.20,
        "quality": 0.08,
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build hybrid hot movie rankings from PostgreSQL stable snapshots and recent Kafka trends."
    )
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument("--top-n", type=int, default=0, help="Override top N rows per period. 0 means use config.")
    return parser.parse_args()


def merge_nested_dict(defaults: dict[str, Any], overrides: dict[str, Any]) -> dict[str, Any]:
    merged = copy.deepcopy(defaults)
    for key, value in overrides.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = merge_nested_dict(merged[key], value)
        else:
            merged[key] = value
    return merged


def resolve_positive_int(raw_value: Any, name: str) -> int:
    value = int(raw_value)
    if value <= 0:
        raise ValueError(f"Invalid {name}: {raw_value}")
    return value


def resolve_period_days(period_days: dict[str, Any] | None) -> dict[str, int]:
    defaults = {"DAILY": 1, "WEEKLY": 7, "MONTHLY": 30}
    if not period_days:
        return defaults

    resolved: dict[str, int] = {}
    for period, default_days in defaults.items():
        raw_value = period_days.get(period, default_days)
        resolved[period] = resolve_positive_int(raw_value, f"period_days[{period}]")
    return resolved


def build_recent_period_metrics(
    recent_source_df: DataFrame,
    calc_date: str,
    period_type: str,
    period_days: int,
) -> tuple[DataFrame, str]:
    calc_date_obj = dt.date.fromisoformat(calc_date)
    start_date = (calc_date_obj - dt.timedelta(days=period_days - 1)).isoformat()

    filtered_df = recent_source_df.where(
        (F.col("dt") >= F.lit(start_date)) & (F.col("dt") <= F.lit(calc_date)) & F.col("movie_id").isNotNull()
    )

    rating_sum = (
        F.sum(F.coalesce(F.col("rating_avg").cast("double"), F.lit(0.0)) * F.coalesce(F.col("rating_cnt"), F.lit(0)))
        .cast("double")
        .alias("rating_sum")
    )

    result_df = (
        filtered_df.groupBy("movie_id")
        .agg(
            F.max("movie_name").alias("movie_name"),
            F.max("movie_year").cast("int").alias("movie_year"),
            F.max("movie_genres").alias("movie_genres"),
            F.max("movie_score").cast("decimal(3,1)").alias("movie_score"),
            F.max("movie_douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
            F.sum(F.coalesce(F.col("view_pv"), F.lit(0))).cast("bigint").alias("recent_view_pv"),
            F.sum(F.coalesce(F.col("view_uv"), F.lit(0))).cast("bigint").alias("recent_view_uv"),
            F.sum(F.coalesce(F.col("rating_cnt"), F.lit(0))).cast("bigint").alias("recent_rating_cnt"),
            rating_sum,
            F.sum(F.coalesce(F.col("comment_cnt"), F.lit(0))).cast("bigint").alias("recent_comment_cnt"),
            F.sum(F.coalesce(F.col("comment_like_cnt"), F.lit(0))).cast("bigint").alias("recent_comment_like_cnt"),
            F.sum(F.coalesce(F.col("favorite_add_cnt"), F.lit(0))).cast("bigint").alias("recent_favorite_add_cnt"),
            F.sum(F.coalesce(F.col("favorite_remove_cnt"), F.lit(0))).cast("bigint").alias("recent_favorite_remove_cnt"),
            F.sum(F.coalesce(F.col("watched_cnt"), F.lit(0))).cast("bigint").alias("recent_watched_cnt"),
            F.sum(F.coalesce(F.col("active_user_cnt"), F.lit(0))).cast("bigint").alias("recent_active_user_cnt"),
            F.sum(F.coalesce(F.col("hot_score").cast("double"), F.lit(0.0))).cast("double").alias("recent_hot_score_raw"),
            F.max("last_event_ts").alias("recent_last_event_ts"),
        )
        .withColumn(
            "recent_rating_avg",
            F.when(F.col("recent_rating_cnt") > 0, F.round(F.col("rating_sum") / F.col("recent_rating_cnt"), 2))
            .otherwise(F.lit(None))
            .cast("decimal(10,2)"),
        )
        .drop("rating_sum")
        .withColumn("period_type", F.lit(period_type))
        .withColumn("window_start", F.lit(start_date))
        .withColumn("window_end", F.lit(calc_date))
    )

    return result_df, start_date


def build_hybrid_hot_ranking(
    base_source_df: DataFrame,
    recent_period_df: DataFrame,
    movie_profile_df: DataFrame,
    period_type: str,
    window_start: str,
    window_end: str,
    top_n: int,
    rerank_weights: dict[str, Any],
) -> DataFrame:
    base_df = base_source_df.where(F.col("movie_id").isNotNull()).select(
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.col("movie_name").alias("base_movie_name"),
        F.col("movie_year").cast("int").alias("base_movie_year"),
        F.col("movie_genres").alias("base_movie_genres"),
        F.col("movie_score").cast("double").alias("base_movie_score"),
        F.col("movie_douban_score").cast("double").alias("base_movie_douban_score"),
        F.coalesce(F.col("hot_score").cast("double"), F.lit(0.0)).alias("base_hot_score_raw"),
        F.col("last_event_ts").alias("base_last_event_ts"),
    )

    recent_df = recent_period_df.select(
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.col("movie_name").alias("recent_movie_name"),
        F.col("movie_year").cast("int").alias("recent_movie_year"),
        F.col("movie_genres").alias("recent_movie_genres"),
        F.col("movie_score").cast("double").alias("recent_movie_score"),
        F.col("movie_douban_score").cast("double").alias("recent_movie_douban_score"),
        F.coalesce(F.col("recent_hot_score_raw"), F.lit(0.0)).cast("double").alias("recent_hot_score_raw"),
        F.col("recent_view_pv").cast("bigint").alias("recent_view_pv"),
        F.col("recent_view_uv").cast("bigint").alias("recent_view_uv"),
        F.col("recent_rating_cnt").cast("bigint").alias("recent_rating_cnt"),
        F.col("recent_rating_avg").cast("decimal(10,2)").alias("recent_rating_avg"),
        F.col("recent_comment_cnt").cast("bigint").alias("recent_comment_cnt"),
        F.col("recent_comment_like_cnt").cast("bigint").alias("recent_comment_like_cnt"),
        F.col("recent_favorite_add_cnt").cast("bigint").alias("recent_favorite_add_cnt"),
        F.col("recent_favorite_remove_cnt").cast("bigint").alias("recent_favorite_remove_cnt"),
        F.col("recent_watched_cnt").cast("bigint").alias("recent_watched_cnt"),
        F.col("recent_active_user_cnt").cast("bigint").alias("recent_active_user_cnt"),
        F.col("recent_last_event_ts").alias("recent_last_event_ts"),
    )

    profile_df = movie_profile_df.select(
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.col("movie_name").alias("profile_movie_name"),
        F.col("movie_year").cast("int").alias("profile_movie_year"),
        F.col("movie_genres").alias("profile_movie_genres"),
        F.col("movie_score").cast("double").alias("profile_movie_score"),
        F.col("movie_douban_score").cast("double").alias("profile_movie_douban_score"),
    )

    candidate_movies_df = (
        base_df.select("movie_id").unionByName(recent_df.select("movie_id")).where(F.col("movie_id").isNotNull()).distinct()
    )

    merged_df = (
        candidate_movies_df.alias("c")
        .join(base_df.alias("b"), on="movie_id", how="left")
        .join(recent_df.alias("r"), on="movie_id", how="left")
        .join(profile_df.alias("p"), on="movie_id", how="left")
        .select(
            F.col("c.movie_id").cast("bigint").alias("movie_id"),
            F.coalesce(F.col("p.profile_movie_name"), F.col("b.base_movie_name"), F.col("r.recent_movie_name")).alias(
                "movie_name"
            ),
            F.coalesce(
                F.col("p.profile_movie_year"),
                F.col("b.base_movie_year"),
                F.col("r.recent_movie_year"),
            )
            .cast("int")
            .alias("movie_year"),
            F.coalesce(F.col("p.profile_movie_genres"), F.col("b.base_movie_genres"), F.col("r.recent_movie_genres")).alias(
                "movie_genres"
            ),
            F.coalesce(
                F.col("p.profile_movie_score"),
                F.col("b.base_movie_score"),
                F.col("r.recent_movie_score"),
            )
            .cast("double")
            .alias("movie_score_raw"),
            F.coalesce(
                F.col("p.profile_movie_douban_score"),
                F.col("b.base_movie_douban_score"),
                F.col("r.recent_movie_douban_score"),
            )
            .cast("double")
            .alias("movie_douban_score_raw"),
            F.coalesce(F.col("b.base_hot_score_raw"), F.lit(0.0)).cast("double").alias("base_hot_score_raw"),
            F.coalesce(F.col("r.recent_hot_score_raw"), F.lit(0.0)).cast("double").alias("recent_hot_score_raw"),
            F.coalesce(F.col("r.recent_view_pv"), F.lit(0)).cast("bigint").alias("recent_view_pv"),
            F.coalesce(F.col("r.recent_view_uv"), F.lit(0)).cast("bigint").alias("recent_view_uv"),
            F.coalesce(F.col("r.recent_rating_cnt"), F.lit(0)).cast("bigint").alias("recent_rating_cnt"),
            F.col("r.recent_rating_avg").cast("decimal(10,2)").alias("recent_rating_avg"),
            F.coalesce(F.col("r.recent_comment_cnt"), F.lit(0)).cast("bigint").alias("recent_comment_cnt"),
            F.coalesce(F.col("r.recent_comment_like_cnt"), F.lit(0)).cast("bigint").alias("recent_comment_like_cnt"),
            F.coalesce(F.col("r.recent_favorite_add_cnt"), F.lit(0)).cast("bigint").alias("recent_favorite_add_cnt"),
            F.coalesce(F.col("r.recent_favorite_remove_cnt"), F.lit(0)).cast("bigint").alias("recent_favorite_remove_cnt"),
            F.coalesce(F.col("r.recent_watched_cnt"), F.lit(0)).cast("bigint").alias("recent_watched_cnt"),
            F.coalesce(F.col("r.recent_active_user_cnt"), F.lit(0)).cast("bigint").alias("recent_active_user_cnt"),
            F.col("b.base_last_event_ts").alias("base_last_event_ts"),
            F.col("r.recent_last_event_ts").alias("recent_last_event_ts"),
        )
        .withColumn("period_type", F.lit(period_type))
        .withColumn("window_start", F.lit(window_start))
        .withColumn("window_end", F.lit(window_end))
    )

    quality_score_cnt = (
        F.when(F.col("movie_score_raw").isNotNull(), F.lit(1)).otherwise(F.lit(0))
        + F.when(F.col("movie_douban_score_raw").isNotNull(), F.lit(1)).otherwise(F.lit(0))
    )

    base_component = F.log1p(F.greatest(F.col("base_hot_score_raw"), F.lit(0.0)))
    recent_component = F.log1p(F.greatest(F.col("recent_hot_score_raw"), F.lit(0.0)))
    trend_ratio_raw = F.when(
        F.col("base_hot_score_raw") > F.lit(0.0),
        F.col("recent_hot_score_raw") / F.col("base_hot_score_raw"),
    ).otherwise(F.col("recent_hot_score_raw"))
    trend_ratio_component = F.log1p(F.greatest(trend_ratio_raw, F.lit(0.0)))
    quality_component = (
        F.when(
            quality_score_cnt > F.lit(0),
            (
                F.coalesce(F.col("movie_score_raw"), F.lit(0.0))
                + F.coalesce(F.col("movie_douban_score_raw"), F.lit(0.0))
            )
            / quality_score_cnt.cast("double")
            / F.lit(10.0),
        )
        .otherwise(F.lit(0.0))
        .cast("double")
    )

    ranked_df = (
        merged_df.withColumn("base_component", base_component)
        .withColumn("recent_component", recent_component)
        .withColumn("trend_ratio_component", trend_ratio_component)
        .withColumn("quality_component", quality_component)
        .withColumn(
            "hybrid_hot_score_raw",
            F.col("base_component") * F.lit(float(rerank_weights.get("base_score", 1.0)))
            + F.col("recent_component") * F.lit(float(rerank_weights.get("recent_trend", 0.35)))
            + F.col("trend_ratio_component") * F.lit(float(rerank_weights.get("trend_ratio", 0.20)))
            + F.col("quality_component") * F.lit(float(rerank_weights.get("quality", 0.08))),
        )
    )

    rank_window = Window.partitionBy("period_type").orderBy(
        F.col("hybrid_hot_score_raw").desc(),
        F.col("recent_hot_score_raw").desc(),
        F.col("base_hot_score_raw").desc(),
        F.col("movie_id").asc(),
    )

    return (
        ranked_df.withColumn("rank_no", F.row_number().over(rank_window))
        .where(F.col("rank_no") <= F.lit(int(top_n)))
        .select(
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("movie_name").alias("movie_name"),
            F.col("movie_year").cast("int").alias("movie_year"),
            F.col("movie_genres").alias("movie_genres"),
            F.col("movie_score_raw").cast("decimal(3,1)").alias("movie_score"),
            F.col("movie_douban_score_raw").cast("decimal(3,1)").alias("movie_douban_score"),
            F.col("period_type").alias("period_type"),
            F.col("rank_no").cast("int").alias("rank_no"),
            F.round(F.col("hybrid_hot_score_raw"), 8).cast("decimal(18,8)").alias("hybrid_hot_score"),
            F.round(F.col("base_hot_score_raw"), 4).cast("decimal(18,4)").alias("base_hot_score"),
            F.round(F.col("recent_hot_score_raw"), 4).cast("decimal(18,4)").alias("recent_hot_score"),
            F.round(F.col("recent_component"), 8).cast("decimal(18,8)").alias("recent_trend_boost"),
            F.round(F.col("trend_ratio_component"), 8).cast("decimal(18,8)").alias("trend_ratio_boost"),
            F.round(F.col("quality_component"), 8).cast("decimal(18,8)").alias("quality_boost"),
            F.col("recent_view_pv").cast("bigint").alias("recent_view_pv"),
            F.col("recent_view_uv").cast("bigint").alias("recent_view_uv"),
            F.col("recent_rating_cnt").cast("bigint").alias("recent_rating_cnt"),
            F.col("recent_rating_avg").cast("decimal(10,2)").alias("recent_rating_avg"),
            F.col("recent_comment_cnt").cast("bigint").alias("recent_comment_cnt"),
            F.col("recent_comment_like_cnt").cast("bigint").alias("recent_comment_like_cnt"),
            F.col("recent_favorite_add_cnt").cast("bigint").alias("recent_favorite_add_cnt"),
            F.col("recent_favorite_remove_cnt").cast("bigint").alias("recent_favorite_remove_cnt"),
            F.col("recent_watched_cnt").cast("bigint").alias("recent_watched_cnt"),
            F.col("recent_active_user_cnt").cast("bigint").alias("recent_active_user_cnt"),
            F.col("window_start").alias("window_start"),
            F.col("window_end").alias("window_end"),
            F.col("base_last_event_ts").alias("base_last_event_ts"),
            F.col("recent_last_event_ts").alias("recent_last_event_ts"),
        )
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    hybrid_config = merge_nested_dict(DEFAULT_ADS_HYBRID_HOT_CONFIG, config.get("ads_hybrid_hot", {}))

    calc_date = args.calc_date
    top_n = args.top_n if args.top_n > 0 else resolve_positive_int(hybrid_config.get("top_n", 100), "top_n")
    period_days = resolve_period_days(hybrid_config.get("period_days"))

    spark = build_spark_session("movie-ads-hybrid-hot-movies", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        base_source_df = spark.table(hybrid_config["base_source_table"]).where(F.col("dt") == calc_date)
        movie_profile_df = spark.table(hybrid_config["movie_profile_source_table"]).where(F.col("dt") == calc_date)

        max_days = max(period_days.values())
        min_date = (dt.date.fromisoformat(calc_date) - dt.timedelta(days=max_days - 1)).isoformat()
        recent_source_df = spark.table(hybrid_config["recent_source_table"]).where(
            (F.col("dt") >= min_date) & (F.col("dt") <= calc_date)
        )

        period_frames: list[DataFrame] = []
        for period_type, days in period_days.items():
            recent_period_df, start_date = build_recent_period_metrics(
                recent_source_df=recent_source_df,
                calc_date=calc_date,
                period_type=period_type,
                period_days=days,
            )
            period_frames.append(
                build_hybrid_hot_ranking(
                    base_source_df=base_source_df,
                    recent_period_df=recent_period_df,
                    movie_profile_df=movie_profile_df,
                    period_type=period_type,
                    window_start=start_date,
                    window_end=calc_date,
                    top_n=top_n,
                    rerank_weights=hybrid_config.get("rerank_weights", {}),
                )
            )

        result_df = period_frames[0]
        for period_df in period_frames[1:]:
            result_df = result_df.unionByName(period_df)

        write_partition(
            result_df,
            hybrid_config["target_table"],
            hybrid_config["sink_path"],
            calc_date,
            spark,
        )

        print(
            "ADS hybrid hot ranking build finished. "
            f"base_source={hybrid_config['base_source_table']}, recent_source={hybrid_config['recent_source_table']}, "
            f"profile_source={hybrid_config['movie_profile_source_table']}, target={hybrid_config['target_table']}, "
            f"dt={calc_date}, top_n={top_n}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
