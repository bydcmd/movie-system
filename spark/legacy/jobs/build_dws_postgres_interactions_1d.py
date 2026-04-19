from __future__ import annotations

import argparse
import copy
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import assert_non_empty_partition, resolve_common_dt_partition_date, write_partition
from utils.spark_factory import build_spark_session


DEFAULT_DWS_POSTGRES_INTERACTIONS_CONFIG: dict[str, Any] = {
    "source_tables": {
        "movie_snapshot": "dwd.dwd_movie_snapshot_di",
        "ratings": "ods.ods_pg_ratings_full",
        "comments": "ods.ods_pg_comments_full",
        "comment_likes": "ods.ods_pg_comment_likes_full",
        "favorites": "ods.ods_pg_favorites_full",
        "view_history": "ods.ods_pg_view_history_full",
        "watched_movies": "ods.ods_pg_watched_movies_full",
    },
    "target_tables": {
        "user_item_preference_1d": "dws.dws_user_item_preference_1d",
        "movie_engagement_1d": "dws.dws_movie_engagement_1d",
        "movie_engagement_daily_1d": "dws.dws_movie_engagement_daily_1d",
    },
    "sink_paths": {
        "user_item_preference_1d": "hdfs:///warehouse/movie/dws/user_item_preference_1d",
        "movie_engagement_1d": "hdfs:///warehouse/movie/dws/movie_engagement_1d",
        "movie_engagement_daily_1d": "hdfs:///warehouse/movie/dws/movie_engagement_daily_1d",
    },
    "preference_score_weights": {
        "view": 1.0,
        "favorite_add": 2.0,
        "comment": 2.0,
        "rating": 3.0,
        "watched": 3.0,
        "rating_value": 2.0,
    },
    "hot_score_weights": {
        "view_pv": 1.0,
        "view_uv": 1.5,
        "rating_cnt": 2.0,
        "comment_cnt": 2.0,
        "comment_like_cnt": 0.8,
        "favorite_add_cnt": 1.2,
        "favorite_remove_cnt": -0.8,
        "watched_cnt": 2.5,
        "active_user_cnt": 1.0,
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build DWS preference and movie engagement snapshots from PostgreSQL ODS.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument(
        "--snapshot-date",
        default="",
        help="Snapshot partition date for PostgreSQL ODS tables. "
        "If omitted, use the latest common dt partition not newer than calc-date.",
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


def load_partition(spark, table_name: str, partition_date: str) -> DataFrame:
    return spark.table(table_name).where(F.col("dt") == partition_date)


def clean_favorites_by_existing_movies(favorites_df: DataFrame, movie_snapshot_df: DataFrame) -> DataFrame:
    valid_movies_df = movie_snapshot_df.select(F.col("movie_id").cast("bigint").alias("movie_id")).distinct()
    return favorites_df.join(valid_movies_df, on="movie_id", how="left_semi")


def union_all(frames: list[DataFrame]) -> DataFrame:
    if not frames:
        raise ValueError("frames must not be empty")

    result = frames[0]
    for frame in frames[1:]:
        result = result.unionByName(frame)
    return result


def build_user_movie_interactions(
    view_history_df: DataFrame,
    ratings_df: DataFrame,
    comments_df: DataFrame,
    favorites_df: DataFrame,
    watched_movies_df: DataFrame,
) -> DataFrame:
    favorite_state_df = (
        favorites_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("create_time").alias("create_time"),
        )
        .groupBy("user_id", "movie_id")
        .agg(F.max("create_time").alias("create_time"))
    )

    watched_state_df = (
        watched_movies_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("create_time").alias("create_time"),
        )
        .groupBy("user_id", "movie_id")
        .agg(F.max("create_time").alias("create_time"))
    )

    view_df = view_history_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull()).select(
        F.col("user_id").cast("string").alias("user_id"),
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.lit("view").alias("interaction_type"),
        F.col("view_time").alias("event_ts"),
        F.lit(None).cast("int").alias("rating_value"),
    )

    rating_df = ratings_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull()).select(
        F.col("user_id").cast("string").alias("user_id"),
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.lit("rating").alias("interaction_type"),
        F.col("rating_time").alias("event_ts"),
        F.col("rating").cast("int").alias("rating_value"),
    )

    comment_df = comments_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull()).select(
        F.col("user_id").cast("string").alias("user_id"),
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.lit("comment").alias("interaction_type"),
        F.col("comment_time").alias("event_ts"),
        F.lit(None).cast("int").alias("rating_value"),
    )

    favorite_df = favorite_state_df.select(
        F.col("user_id").cast("string").alias("user_id"),
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.lit("favorite_add").alias("interaction_type"),
        F.col("create_time").alias("event_ts"),
        F.lit(None).cast("int").alias("rating_value"),
    )

    watched_df = watched_state_df.select(
        F.col("user_id").cast("string").alias("user_id"),
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.lit("watched").alias("interaction_type"),
        F.col("create_time").alias("event_ts"),
        F.lit(None).cast("int").alias("rating_value"),
    )

    return union_all([view_df, rating_df, comment_df, favorite_df, watched_df])


def build_movie_comment_like_metrics(comment_likes_df: DataFrame, comments_df: DataFrame) -> DataFrame:
    return (
        comment_likes_df.alias("cl")
        .join(comments_df.alias("c"), F.col("cl.comment_id") == F.col("c.comment_id"), "inner")
        .where(F.col("c.movie_id").isNotNull())
        .select(
            F.col("c.movie_id").cast("bigint").alias("movie_id"),
            F.col("cl.create_time").alias("event_ts"),
        )
    )


def aggregate_movie_comment_like_metrics(comment_like_events_df: DataFrame) -> DataFrame:
    return (
        comment_like_events_df.groupBy("movie_id")
        .agg(
            F.count(F.lit(1)).cast("bigint").alias("comment_like_cnt"),
            F.max("event_ts").alias("comment_like_last_event_ts"),
        )
    )


def filter_interactions_by_calc_date(interactions_df: DataFrame, calc_date: str) -> DataFrame:
    return interactions_df.where(F.to_date(F.col("event_ts")) == F.lit(calc_date).cast("date"))


def filter_comment_like_events_by_calc_date(comment_like_events_df: DataFrame, calc_date: str) -> DataFrame:
    return comment_like_events_df.where(F.to_date(F.col("event_ts")) == F.lit(calc_date).cast("date"))


def build_user_item_preference(interactions_df: DataFrame, weights: dict[str, Any]) -> DataFrame:
    rating_sum_expr = F.sum(F.when(F.col("interaction_type") == "rating", F.coalesce(F.col("rating_value"), F.lit(0))).otherwise(0))

    aggregated_df = interactions_df.groupBy("user_id", "movie_id").agg(
        F.sum(F.when(F.col("interaction_type") == "view", 1).otherwise(0)).cast("bigint").alias("view_cnt"),
        F.sum(F.when(F.col("interaction_type") == "rating", 1).otherwise(0)).cast("bigint").alias("rating_cnt"),
        rating_sum_expr.cast("double").alias("rating_sum"),
        F.round(F.avg(F.when(F.col("interaction_type") == "rating", F.col("rating_value").cast("double"))), 2)
        .cast("decimal(10,2)")
        .alias("rating_avg"),
        F.sum(F.when(F.col("interaction_type") == "comment", 1).otherwise(0)).cast("bigint").alias("comment_cnt"),
        F.sum(F.when(F.col("interaction_type") == "favorite_add", 1).otherwise(0))
        .cast("bigint")
        .alias("favorite_add_cnt"),
        F.lit(0).cast("bigint").alias("favorite_remove_cnt"),
        F.sum(F.when(F.col("interaction_type") == "watched", 1).otherwise(0)).cast("bigint").alias("watched_cnt"),
        F.max("event_ts").alias("last_event_ts"),
    )

    preference_score_expr = (
        F.col("view_cnt") * F.lit(float(weights.get("view", 1.0)))
        + F.col("favorite_add_cnt") * F.lit(float(weights.get("favorite_add", weights.get("favorite", 2.0))))
        + F.col("comment_cnt") * F.lit(float(weights.get("comment", 2.0)))
        + F.col("rating_cnt") * F.lit(float(weights.get("rating", 3.0)))
        + F.col("watched_cnt") * F.lit(float(weights.get("watched", 3.0)))
        + (F.col("rating_sum") / F.lit(5.0)) * F.lit(float(weights.get("rating_value", 2.0)))
    )

    return (
        aggregated_df.withColumn("preference_score", F.round(preference_score_expr, 8).cast("decimal(18,8)"))
        .drop("rating_sum")
        .where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            "view_cnt",
            "rating_cnt",
            "rating_avg",
            "comment_cnt",
            "favorite_add_cnt",
            "favorite_remove_cnt",
            "watched_cnt",
            "preference_score",
            "last_event_ts",
        )
    )


def build_movie_engagement(
    interactions_df: DataFrame,
    movie_snapshot_df: DataFrame,
    movie_comment_likes_df: DataFrame,
    weights: dict[str, Any],
) -> DataFrame:
    aggregated_df = interactions_df.groupBy("movie_id").agg(
        F.sum(F.when(F.col("interaction_type") == "view", 1).otherwise(0)).cast("bigint").alias("view_pv"),
        F.countDistinct(F.when(F.col("interaction_type") == "view", F.col("user_id"))).cast("bigint").alias("view_uv"),
        F.sum(F.when(F.col("interaction_type") == "rating", 1).otherwise(0)).cast("bigint").alias("rating_cnt"),
        F.round(F.avg(F.when(F.col("interaction_type") == "rating", F.col("rating_value").cast("double"))), 2)
        .cast("decimal(10,2)")
        .alias("rating_avg"),
        F.sum(F.when(F.col("interaction_type") == "comment", 1).otherwise(0)).cast("bigint").alias("comment_cnt"),
        F.sum(F.when(F.col("interaction_type") == "favorite_add", 1).otherwise(0))
        .cast("bigint")
        .alias("favorite_add_cnt"),
        F.lit(0).cast("bigint").alias("favorite_remove_cnt"),
        F.sum(F.when(F.col("interaction_type") == "watched", 1).otherwise(0)).cast("bigint").alias("watched_cnt"),
        F.countDistinct("user_id").cast("bigint").alias("active_user_cnt"),
        F.max("event_ts").alias("base_last_event_ts"),
    )

    candidate_movies_df = (
        aggregated_df.select(F.col("movie_id").cast("bigint").alias("movie_id"))
        .unionByName(movie_comment_likes_df.select(F.col("movie_id").cast("bigint").alias("movie_id")))
        .where(F.col("movie_id").isNotNull())
        .distinct()
    )

    joined_df = (
        candidate_movies_df.alias("cm")
        .join(aggregated_df.alias("m"), F.col("cm.movie_id") == F.col("m.movie_id"), "left")
        .join(movie_snapshot_df.alias("s"), F.col("cm.movie_id") == F.col("s.movie_id"), "left")
        .join(movie_comment_likes_df.alias("l"), F.col("cm.movie_id") == F.col("l.movie_id"), "left")
    )

    hot_score_expr = (
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

    return (
        joined_df.select(
            F.col("cm.movie_id").cast("bigint").alias("movie_id"),
            F.coalesce(F.col("s.movie_name"), F.lit(None)).alias("movie_name"),
            F.col("s.movie_year").cast("int").alias("movie_year"),
            F.col("s.movie_genres").alias("movie_genres"),
            F.col("s.movie_score").cast("decimal(3,1)").alias("movie_score"),
            F.col("s.movie_douban_score").cast("decimal(3,1)").alias("movie_douban_score"),
            F.coalesce(F.col("m.view_pv"), F.lit(0)).cast("bigint").alias("view_pv"),
            F.coalesce(F.col("m.view_uv"), F.lit(0)).cast("bigint").alias("view_uv"),
            F.coalesce(F.col("m.rating_cnt"), F.lit(0)).cast("bigint").alias("rating_cnt"),
            F.col("m.rating_avg").cast("decimal(10,2)").alias("rating_avg"),
            F.coalesce(F.col("m.comment_cnt"), F.lit(0)).cast("bigint").alias("comment_cnt"),
            F.coalesce(F.col("l.comment_like_cnt"), F.lit(0)).cast("bigint").alias("comment_like_cnt"),
            F.coalesce(F.col("m.favorite_add_cnt"), F.lit(0)).cast("bigint").alias("favorite_add_cnt"),
            F.coalesce(F.col("m.favorite_remove_cnt"), F.lit(0)).cast("bigint").alias("favorite_remove_cnt"),
            F.coalesce(F.col("m.watched_cnt"), F.lit(0)).cast("bigint").alias("watched_cnt"),
            F.coalesce(F.col("m.active_user_cnt"), F.lit(0)).cast("bigint").alias("active_user_cnt"),
            F.greatest(F.col("m.base_last_event_ts"), F.col("l.comment_like_last_event_ts")).alias("last_event_ts"),
            F.col("s.source_snapshot_dt").alias("source_snapshot_dt"),
        )
        .withColumn("hot_score", F.round(hot_score_expr, 4).cast("decimal(18,4)"))
        .where(F.col("movie_id").isNotNull())
        .select(
            "movie_id",
            "movie_name",
            "movie_year",
            "movie_genres",
            "movie_score",
            "movie_douban_score",
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
            "hot_score",
            "last_event_ts",
            "source_snapshot_dt",
        )
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    dws_config = merge_nested_dict(DEFAULT_DWS_POSTGRES_INTERACTIONS_CONFIG, config.get("dws_postgres_interactions", {}))

    calc_date = args.calc_date
    requested_snapshot_date = args.snapshot_date.strip()

    spark = build_spark_session("movie-dws-postgres-interactions-1d", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS dws")

        source_tables = dws_config["source_tables"]
        snapshot_date = resolve_common_dt_partition_date(
            [
                source_tables["movie_snapshot"],
                source_tables["ratings"],
                source_tables["comments"],
                source_tables["comment_likes"],
                source_tables["favorites"],
                source_tables["view_history"],
                source_tables["watched_movies"],
            ],
            requested_snapshot_date,
            spark,
            fallback_max_date=calc_date,
        )
        movie_snapshot_df = load_partition(spark, source_tables["movie_snapshot"], snapshot_date)
        assert_non_empty_partition(movie_snapshot_df, source_tables["movie_snapshot"], {"dt": snapshot_date})
        ratings_df = load_partition(spark, source_tables["ratings"], snapshot_date)
        comments_df = load_partition(spark, source_tables["comments"], snapshot_date)
        comment_likes_df = load_partition(spark, source_tables["comment_likes"], snapshot_date)
        favorites_df = load_partition(spark, source_tables["favorites"], snapshot_date)
        view_history_df = load_partition(spark, source_tables["view_history"], snapshot_date)
        watched_movies_df = load_partition(spark, source_tables["watched_movies"], snapshot_date)
        favorites_df = clean_favorites_by_existing_movies(favorites_df, movie_snapshot_df)

        interactions_df = build_user_movie_interactions(
            view_history_df=view_history_df,
            ratings_df=ratings_df,
            comments_df=comments_df,
            favorites_df=favorites_df,
            watched_movies_df=watched_movies_df,
        )
        comment_like_events_df = build_movie_comment_like_metrics(comment_likes_df, comments_df)
        movie_comment_likes_df = aggregate_movie_comment_like_metrics(comment_like_events_df)
        daily_interactions_df = filter_interactions_by_calc_date(interactions_df, calc_date)
        daily_comment_like_events_df = filter_comment_like_events_by_calc_date(comment_like_events_df, calc_date)
        daily_movie_comment_likes_df = aggregate_movie_comment_like_metrics(daily_comment_like_events_df)

        user_item_df = build_user_item_preference(
            interactions_df=interactions_df,
            weights=dws_config.get("preference_score_weights", {}),
        ).withColumn("source_snapshot_dt", F.lit(snapshot_date))

        movie_engagement_df = build_movie_engagement(
            interactions_df=interactions_df,
            movie_snapshot_df=movie_snapshot_df,
            movie_comment_likes_df=movie_comment_likes_df,
            weights=dws_config.get("hot_score_weights", {}),
        )
        daily_movie_engagement_df = build_movie_engagement(
            interactions_df=daily_interactions_df,
            movie_snapshot_df=movie_snapshot_df,
            movie_comment_likes_df=daily_movie_comment_likes_df,
            weights=dws_config.get("hot_score_weights", {}),
        )

        write_partition(
            user_item_df,
            dws_config["target_tables"]["user_item_preference_1d"],
            dws_config["sink_paths"]["user_item_preference_1d"],
            calc_date,
            spark,
        )
        write_partition(
            movie_engagement_df,
            dws_config["target_tables"]["movie_engagement_1d"],
            dws_config["sink_paths"]["movie_engagement_1d"],
            calc_date,
            spark,
        )
        write_partition(
            daily_movie_engagement_df,
            dws_config["target_tables"]["movie_engagement_daily_1d"],
            dws_config["sink_paths"]["movie_engagement_daily_1d"],
            calc_date,
            spark,
        )

        print(
            "DWS PostgreSQL interaction build finished. "
            f"user_item_target={dws_config['target_tables']['user_item_preference_1d']}, "
            f"movie_target={dws_config['target_tables']['movie_engagement_1d']}, "
            f"movie_daily_target={dws_config['target_tables']['movie_engagement_daily_1d']}, "
            f"dt={calc_date}, source_snapshot_dt={snapshot_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
