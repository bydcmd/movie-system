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


DEFAULT_ADS_HYBRID_RECO_CONFIG: dict[str, Any] = {
    "base_source_table": "ads.ads_itemcf_user_recommendations",
    "recent_event_source_table": "dwd.dwd_user_event_wide_di",
    "recent_movie_metric_source_table": "dws.dws_movie_action_1d",
    "movie_profile_source_table": "dws.dws_movie_profile_1d",
    "target_table": "ads.ads_hybrid_user_recommendations",
    "sink_path": "hdfs:///warehouse/movie/ads/hybrid_user_recommendations",
    "lookback_days": 7,
    "candidate_top_n": 100,
    "top_n": 30,
    "exclude_recently_interacted": True,
    "rerank_weights": {
        "base_score": 1.0,
        "genre_match": 0.35,
        "popularity": 0.12,
        "quality": 0.08,
        "recent_interaction_penalty": 0.60,
    },
    "event_score_weights": {
        "view": 1.0,
        "favorite_add": 2.0,
        "favorite_remove": -1.0,
        "comment": 2.0,
        "rating": 3.0,
        "watched": 3.0,
        "rating_value": 2.0,
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build hybrid user recommendations from ItemCF recall and recent Kafka behavior.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument("--top-n", type=int, default=0, help="Override final top N recommendations per user. 0 means use config.")
    parser.add_argument(
        "--candidate-top-n",
        type=int,
        default=0,
        help="Override candidate top N from base recall. 0 means use config.",
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


def resolve_positive_int(raw_value: Any, name: str) -> int:
    value = int(raw_value)
    if value <= 0:
        raise ValueError(f"Invalid {name}: {raw_value}")
    return value


def build_recent_event_score_expr(weights: dict[str, Any]) -> F.Column:
    rating_value = F.coalesce(F.col("rating"), F.col("rating_snapshot")).cast("double")
    operation_norm = F.upper(F.trim(F.coalesce(F.col("operation_norm"), F.col("operation"), F.lit(""))))
    favorite_add_weight = float(weights.get("favorite_add", weights.get("favorite", 2.0)))
    favorite_remove_weight = float(weights.get("favorite_remove", -1.0))

    return (
        F.coalesce(F.col("is_view"), F.lit(0)) * F.lit(float(weights.get("view", 1.0)))
        + F.when((F.col("is_favorite") == 1) & (operation_norm == "ADD"), F.lit(favorite_add_weight))
        .when((F.col("is_favorite") == 1) & (operation_norm == "REMOVE"), F.lit(favorite_remove_weight))
        .otherwise(F.lit(0.0))
        + F.coalesce(F.col("is_comment"), F.lit(0)) * F.lit(float(weights.get("comment", 2.0)))
        + F.coalesce(F.col("is_rating"), F.lit(0)) * F.lit(float(weights.get("rating", 3.0)))
        + F.coalesce(F.col("is_watched"), F.lit(0)) * F.lit(float(weights.get("watched", 3.0)))
        + F.when(F.col("is_rating") == 1, F.coalesce(rating_value, F.lit(0.0)) / F.lit(5.0)).otherwise(F.lit(0.0))
        * F.lit(float(weights.get("rating_value", 2.0)))
    )


def build_recent_item_signals(recent_events_df: DataFrame, weights: dict[str, Any]) -> DataFrame:
    return (
        recent_events_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .withColumn("recent_event_score", build_recent_event_score_expr(weights).cast("double"))
        .groupBy("user_id", "movie_id")
        .agg(
            F.sum("recent_event_score").cast("double").alias("recent_interaction_score"),
            F.max("event_ts").alias("last_recent_event_ts"),
        )
    )


def build_recent_user_genre_signals(
    recent_events_df: DataFrame,
    movie_profile_df: DataFrame,
    weights: dict[str, Any],
) -> DataFrame:
    scored_events_df = (
        recent_events_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .withColumn("recent_event_score", build_recent_event_score_expr(weights).cast("double"))
        .select("user_id", "movie_id", "recent_event_score")
    )

    movie_genre_df = movie_profile_df.select(
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.explode(F.split(F.coalesce(F.col("movie_genres"), F.lit("")), ",")).alias("genre"),
    ).withColumn("genre", F.trim(F.col("genre"))).where(F.col("genre") != "")

    return (
        scored_events_df.join(movie_genre_df, on="movie_id", how="inner")
        .groupBy("user_id", "genre")
        .agg(F.sum("recent_event_score").cast("double").alias("recent_genre_score"))
    )


def build_candidate_genre_match(
    base_candidates_df: DataFrame,
    movie_profile_df: DataFrame,
    recent_user_genre_df: DataFrame,
) -> DataFrame:
    candidate_genre_df = (
        base_candidates_df.select("user_id", "movie_id")
        .join(
            movie_profile_df.select(
                F.col("movie_id").cast("bigint").alias("movie_id"),
                F.explode(F.split(F.coalesce(F.col("movie_genres"), F.lit("")), ",")).alias("genre"),
            ),
            on="movie_id",
            how="left",
        )
        .withColumn("genre", F.trim(F.col("genre")))
        .where(F.col("genre") != "")
    )

    return (
        candidate_genre_df.join(recent_user_genre_df, on=["user_id", "genre"], how="left")
        .groupBy("user_id", "movie_id")
        .agg(F.sum(F.coalesce(F.col("recent_genre_score"), F.lit(0.0))).cast("double").alias("genre_match_raw"))
    )


def build_recent_movie_popularity(recent_movie_metric_df: DataFrame) -> DataFrame:
    return (
        recent_movie_metric_df.where(F.col("movie_id").isNotNull())
        .groupBy("movie_id")
        .agg(
            F.sum(F.coalesce(F.col("hot_score").cast("double"), F.lit(0.0))).cast("double").alias("recent_movie_hot_score"),
            F.max("last_event_ts").alias("recent_movie_last_event_ts"),
        )
    )


def signed_log1p(column_name: str) -> F.Column:
    column_expr = F.coalesce(F.col(column_name), F.lit(0.0)).cast("double")
    return (
        F.when(column_expr > F.lit(0.0), F.log1p(column_expr))
        .when(column_expr < F.lit(0.0), -F.log1p(F.abs(column_expr)))
        .otherwise(F.lit(0.0))
    )


def build_hybrid_recommendations(
    base_candidates_df: DataFrame,
    movie_profile_df: DataFrame,
    recent_movie_popularity_df: DataFrame,
    recent_item_df: DataFrame,
    genre_match_df: DataFrame,
    top_n: int,
    rerank_weights: dict[str, Any],
    exclude_recently_interacted: bool,
) -> DataFrame:
    movie_feature_df = movie_profile_df.select(
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.col("movie_name").alias("movie_name"),
        F.col("movie_year").cast("int").alias("movie_year"),
        F.col("movie_genres").alias("movie_genres"),
        F.col("movie_score").cast("decimal(3,1)").alias("movie_score"),
        F.col("movie_douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
        F.col("hot_score").cast("double").alias("movie_profile_hot_score"),
    )

    merged_df = (
        base_candidates_df.alias("b")
        .join(movie_feature_df.alias("m"), on="movie_id", how="left")
        .join(recent_movie_popularity_df.alias("p"), on="movie_id", how="left")
        .join(recent_item_df.alias("r"), on=["user_id", "movie_id"], how="left")
        .join(genre_match_df.alias("g"), on=["user_id", "movie_id"], how="left")
        .select(
            F.col("b.user_id").cast("string").alias("user_id"),
            F.col("b.movie_id").cast("bigint").alias("movie_id"),
            F.col("b.rank_no").cast("int").alias("base_rank_no"),
            F.col("b.recommend_score").cast("double").alias("base_recommend_score_raw"),
            F.col("b.seed_item_cnt").cast("bigint").alias("seed_item_cnt"),
            F.col("b.best_similarity").cast("double").alias("best_similarity_raw"),
            F.col("m.movie_name").alias("movie_name"),
            F.col("m.movie_year").cast("int").alias("movie_year"),
            F.col("m.movie_genres").alias("movie_genres"),
            F.col("m.movie_score").cast("double").alias("movie_score_raw"),
            F.col("m.movie_douban_score").cast("double").alias("movie_douban_score_raw"),
            F.coalesce(F.col("p.recent_movie_hot_score"), F.col("m.movie_profile_hot_score"), F.lit(0.0))
            .cast("double")
            .alias("movie_hot_score_raw"),
            F.coalesce(F.col("g.genre_match_raw"), F.lit(0.0)).cast("double").alias("genre_match_raw"),
            F.coalesce(F.col("r.recent_interaction_score"), F.lit(0.0)).cast("double").alias("recent_interaction_score"),
            F.greatest(F.col("r.last_recent_event_ts"), F.col("p.recent_movie_last_event_ts")).alias("last_recent_event_ts"),
        )
        .withColumn(
            "is_recently_interacted",
            F.when(F.col("recent_interaction_score") > F.lit(0.0), F.lit(1)).otherwise(F.lit(0)).cast("tinyint"),
        )
    )

    if exclude_recently_interacted:
        merged_df = merged_df.where(F.col("is_recently_interacted") == 0)

    quality_score_cnt = (
        F.when(F.col("movie_score_raw").isNotNull(), F.lit(1)).otherwise(F.lit(0))
        + F.when(F.col("movie_douban_score_raw").isNotNull(), F.lit(1)).otherwise(F.lit(0))
    )

    base_component = F.log1p(F.greatest(F.col("base_recommend_score_raw"), F.lit(0.0)))
    genre_component = signed_log1p("genre_match_raw")
    popularity_component = F.log1p(F.greatest(F.col("movie_hot_score_raw"), F.lit(0.0)))
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
    recent_penalty_component = F.when(
        F.col("recent_interaction_score") > F.lit(0.0),
        F.log1p(F.col("recent_interaction_score")),
    ).otherwise(F.lit(0.0))

    reranked_df = (
        merged_df.withColumn("base_component", base_component)
        .withColumn("genre_match_component", genre_component)
        .withColumn("popularity_component", popularity_component)
        .withColumn("quality_component", quality_component)
        .withColumn("recent_penalty_component", recent_penalty_component)
        .withColumn(
            "rerank_score_raw",
            F.col("base_component") * F.lit(float(rerank_weights.get("base_score", 1.0)))
            + F.col("genre_match_component") * F.lit(float(rerank_weights.get("genre_match", 0.35)))
            + F.col("popularity_component") * F.lit(float(rerank_weights.get("popularity", 0.12)))
            + F.col("quality_component") * F.lit(float(rerank_weights.get("quality", 0.08)))
            - F.col("recent_penalty_component")
            * F.lit(float(rerank_weights.get("recent_interaction_penalty", 0.60))),
        )
    )

    rank_window = Window.partitionBy("user_id").orderBy(
        F.col("rerank_score_raw").desc(),
        F.col("base_recommend_score_raw").desc(),
        F.col("movie_id").asc(),
    )

    return (
        reranked_df.withColumn("rank_no", F.row_number().over(rank_window))
        .where(F.col("rank_no") <= F.lit(int(top_n)))
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("rank_no").cast("int").alias("rank_no"),
            F.col("base_rank_no").cast("int").alias("base_rank_no"),
            F.round(F.col("rerank_score_raw"), 8).cast("decimal(18,8)").alias("rerank_score"),
            F.round(F.col("base_recommend_score_raw"), 8).cast("decimal(18,8)").alias("base_recommend_score"),
            F.round(F.col("genre_match_component"), 8).cast("decimal(18,8)").alias("genre_match_score"),
            F.round(F.col("popularity_component"), 8).cast("decimal(18,8)").alias("popularity_boost"),
            F.round(F.col("quality_component"), 8).cast("decimal(18,8)").alias("quality_boost"),
            F.round(F.col("recent_penalty_component"), 8).cast("decimal(18,8)").alias("recent_interaction_penalty"),
            F.col("seed_item_cnt").cast("bigint").alias("seed_item_cnt"),
            F.round(F.col("best_similarity_raw"), 8).cast("decimal(18,8)").alias("best_similarity"),
            F.col("movie_name").alias("movie_name"),
            F.col("movie_year").cast("int").alias("movie_year"),
            F.col("movie_genres").alias("movie_genres"),
            F.col("movie_score_raw").cast("decimal(3,1)").alias("movie_score"),
            F.col("movie_douban_score_raw").cast("decimal(3,1)").alias("movie_douban_score"),
            F.round(F.col("movie_hot_score_raw"), 4).cast("decimal(18,4)").alias("movie_hot_score"),
            F.col("is_recently_interacted").cast("tinyint").alias("is_recently_interacted"),
            F.col("last_recent_event_ts").alias("last_recent_event_ts"),
        )
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    hybrid_config = merge_nested_dict(DEFAULT_ADS_HYBRID_RECO_CONFIG, config.get("ads_hybrid_reco", {}))

    calc_date = args.calc_date
    lookback_days = resolve_positive_int(hybrid_config.get("lookback_days", 7), "lookback_days")
    candidate_top_n = (
        args.candidate_top_n
        if args.candidate_top_n > 0
        else resolve_positive_int(hybrid_config.get("candidate_top_n", 100), "candidate_top_n")
    )
    top_n = args.top_n if args.top_n > 0 else resolve_positive_int(hybrid_config.get("top_n", 30), "top_n")

    spark = build_spark_session("movie-ads-hybrid-user-recommendations", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        base_candidates_df = (
            spark.table(hybrid_config["base_source_table"])
            .where(F.col("dt") == calc_date)
            .where(F.col("rank_no") <= F.lit(int(candidate_top_n)))
        )
        movie_profile_df = spark.table(hybrid_config["movie_profile_source_table"]).where(F.col("dt") == calc_date)

        start_date = (dt.date.fromisoformat(calc_date) - dt.timedelta(days=lookback_days - 1)).isoformat()
        recent_events_df = spark.table(hybrid_config["recent_event_source_table"]).where(
            (F.col("dt") >= start_date) & (F.col("dt") <= calc_date)
        )
        recent_movie_metric_df = spark.table(hybrid_config["recent_movie_metric_source_table"]).where(
            (F.col("dt") >= start_date) & (F.col("dt") <= calc_date)
        )

        recent_item_df = build_recent_item_signals(
            recent_events_df=recent_events_df,
            weights=hybrid_config.get("event_score_weights", {}),
        )
        recent_movie_popularity_df = build_recent_movie_popularity(recent_movie_metric_df)
        recent_user_genre_df = build_recent_user_genre_signals(
            recent_events_df=recent_events_df,
            movie_profile_df=movie_profile_df,
            weights=hybrid_config.get("event_score_weights", {}),
        )
        genre_match_df = build_candidate_genre_match(
            base_candidates_df=base_candidates_df,
            movie_profile_df=movie_profile_df,
            recent_user_genre_df=recent_user_genre_df,
        )

        result_df = build_hybrid_recommendations(
            base_candidates_df=base_candidates_df,
            movie_profile_df=movie_profile_df,
            recent_movie_popularity_df=recent_movie_popularity_df,
            recent_item_df=recent_item_df,
            genre_match_df=genre_match_df,
            top_n=top_n,
            rerank_weights=hybrid_config.get("rerank_weights", {}),
            exclude_recently_interacted=bool(hybrid_config.get("exclude_recently_interacted", True)),
        )

        write_partition(
            result_df,
            hybrid_config["target_table"],
            hybrid_config["sink_path"],
            calc_date,
            spark,
        )

        print(
            "ADS hybrid recommendation build finished. "
            f"base_source={hybrid_config['base_source_table']}, recent_source={hybrid_config['recent_event_source_table']}, "
            f"movie_source={hybrid_config['movie_profile_source_table']}, "
            f"recent_movie_source={hybrid_config['recent_movie_metric_source_table']}, target={hybrid_config['target_table']}, "
            f"dt={calc_date}, lookback_days={lookback_days}, candidate_top_n={candidate_top_n}, top_n={top_n}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
