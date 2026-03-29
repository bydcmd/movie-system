from __future__ import annotations

import argparse
import copy
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql.column import Column
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import assert_non_empty_partition, resolve_common_dt_partition_date, write_partition
from utils.spark_factory import build_spark_session


DEFAULT_DWS_PROFILES_CONFIG: dict[str, Any] = {
    "source_tables": {
        "user_snapshot": "dwd.dwd_user_snapshot_di",
        "movie_snapshot": "dwd.dwd_movie_snapshot_di",
        "user_action": "dws.dws_user_action_1d",
        "movie_action": "dws.dws_movie_action_1d",
    },
    "target_tables": {
        "user_profile_1d": "dws.dws_user_profile_1d",
        "movie_profile_1d": "dws.dws_movie_profile_1d",
    },
    "sink_paths": {
        "user_profile_1d": "hdfs:///warehouse/movie/dws/user_profile_1d",
        "movie_profile_1d": "hdfs:///warehouse/movie/dws/movie_profile_1d",
    },
    "user_activity_score_weights": {
        "view_cnt": 1.0,
        "rating_cnt": 3.0,
        "comment_cnt": 3.0,
        "comment_like_cnt": 0.5,
        "favorite_add_cnt": 2.0,
        "favorite_remove_cnt": -1.0,
        "watched_cnt": 3.0,
        "search_cnt": 1.0,
        "login_cnt": 0.5,
        "favorite_folder_action_cnt": 1.5,
    },
    "user_activity_level_thresholds": {
        "high": 20.0,
        "medium": 5.0,
    },
    "movie_popularity_level_thresholds": {
        "hot": 1000.0,
        "warm": 100.0,
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build DWS user/movie profile tables from DWD snapshots and DWS metrics.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument(
        "--snapshot-date",
        default="",
        help="Snapshot partition date for DWD snapshot tables. "
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


def coalesce_metric(column_name: str, data_type: str) -> Column:
    return F.coalesce(F.col(column_name), F.lit(0)).cast(data_type)


def optional_source_column(
    available_columns: set[str],
    qualified_column_name: str,
    data_type: str | None = None,
) -> Column:
    column_name = qualified_column_name.rsplit(".", maxsplit=1)[-1]
    column = F.col(qualified_column_name) if column_name in available_columns else F.lit(None)
    return column.cast(data_type) if data_type is not None else column


def coalesce_optional_metric(
    available_columns: set[str],
    qualified_column_name: str,
    data_type: str,
) -> Column:
    return F.coalesce(optional_source_column(available_columns, qualified_column_name, data_type), F.lit(0)).cast(
        data_type
    )


def build_user_profile(
    user_snapshot_df: DataFrame,
    user_action_df: DataFrame,
    calc_date: str,
    weights: dict[str, Any],
    thresholds: dict[str, Any],
) -> DataFrame:
    snapshot_df = user_snapshot_df.alias("s")
    action_df = user_action_df.alias("a")
    action_columns = set(user_action_df.columns)
    expected_action_columns = {
        "user_nickname",
        "user_role",
        "user_status",
        "view_cnt",
        "rating_cnt",
        "rating_avg",
        "comment_cnt",
        "comment_like_cnt",
        "favorite_add_cnt",
        "favorite_remove_cnt",
        "watched_cnt",
        "search_cnt",
        "login_cnt",
        "favorite_folder_action_cnt",
        "active_movie_cnt",
        "last_event_ts",
    }
    missing_action_columns = sorted(expected_action_columns - action_columns)
    if missing_action_columns:
        print(
            "dws_user_action_1d is missing expected columns; defaulting to null/0 where needed: "
            + ", ".join(missing_action_columns)
        )

    joined_df = snapshot_df.join(action_df, F.col("s.user_id") == F.col("a.user_id"), "full")

    calc_date_expr = F.lit(calc_date).cast("date")
    email_value = F.trim(F.coalesce(F.col("s.email"), F.lit("")))
    email_domain = F.lower(F.regexp_extract(email_value, "@(.+)$", 1))
    action_last_event_ts = optional_source_column(action_columns, "a.last_event_ts")

    base_df = joined_df.select(
        F.coalesce(F.col("s.user_id"), F.col("a.user_id")).cast("string").alias("user_id"),
        F.coalesce(
            F.col("s.user_nickname"),
            optional_source_column(action_columns, "a.user_nickname"),
        ).alias("user_nickname"),
        F.col("s.user_avatar").alias("user_avatar"),
        F.col("s.user_url").alias("user_url"),
        F.coalesce(
            F.col("s.user_role"),
            optional_source_column(action_columns, "a.user_role", "tinyint"),
        )
        .cast("tinyint")
        .alias("user_role"),
        F.coalesce(
            F.col("s.user_status"),
            optional_source_column(action_columns, "a.user_status", "int"),
        )
        .cast("int")
        .alias("user_status"),
        F.when(email_value != "", F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("has_email"),
        F.when(email_domain != "", email_domain).otherwise(F.lit(None)).alias("email_domain"),
        F.col("s.register_date").alias("register_date"),
        F.col("s.update_date").alias("update_date"),
        F.when(
            F.to_date(F.col("s.register_date")).isNotNull(),
            F.greatest(F.datediff(calc_date_expr, F.to_date(F.col("s.register_date"))), F.lit(0)),
        )
        .otherwise(F.lit(None))
        .cast("int")
        .alias("register_age_days"),
        coalesce_optional_metric(action_columns, "a.view_cnt", "bigint").alias("view_cnt"),
        coalesce_optional_metric(action_columns, "a.rating_cnt", "bigint").alias("rating_cnt"),
        optional_source_column(action_columns, "a.rating_avg", "decimal(10,2)").alias("rating_avg"),
        coalesce_optional_metric(action_columns, "a.comment_cnt", "bigint").alias("comment_cnt"),
        coalesce_optional_metric(action_columns, "a.comment_like_cnt", "bigint").alias("comment_like_cnt"),
        coalesce_optional_metric(action_columns, "a.favorite_add_cnt", "bigint").alias("favorite_add_cnt"),
        coalesce_optional_metric(action_columns, "a.favorite_remove_cnt", "bigint").alias("favorite_remove_cnt"),
        coalesce_optional_metric(action_columns, "a.watched_cnt", "bigint").alias("watched_cnt"),
        coalesce_optional_metric(action_columns, "a.search_cnt", "bigint").alias("search_cnt"),
        coalesce_optional_metric(action_columns, "a.login_cnt", "bigint").alias("login_cnt"),
        coalesce_optional_metric(action_columns, "a.favorite_folder_action_cnt", "bigint").alias(
            "favorite_folder_action_cnt"
        ),
        coalesce_optional_metric(action_columns, "a.active_movie_cnt", "bigint").alias("active_movie_cnt"),
        action_last_event_ts.alias("last_event_ts"),
        F.when(action_last_event_ts.isNotNull(), F.lit(1))
        .otherwise(F.lit(0))
        .cast("tinyint")
        .alias("is_active_today"),
        F.col("s.source_snapshot_dt").alias("source_snapshot_dt"),
    )

    activity_score_expr = (
        F.col("view_cnt") * F.lit(float(weights.get("view_cnt", 1.0)))
        + F.col("rating_cnt") * F.lit(float(weights.get("rating_cnt", 3.0)))
        + F.col("comment_cnt") * F.lit(float(weights.get("comment_cnt", 3.0)))
        + F.col("comment_like_cnt") * F.lit(float(weights.get("comment_like_cnt", 0.5)))
        + F.col("favorite_add_cnt") * F.lit(float(weights.get("favorite_add_cnt", 2.0)))
        + F.col("favorite_remove_cnt") * F.lit(float(weights.get("favorite_remove_cnt", -1.0)))
        + F.col("watched_cnt") * F.lit(float(weights.get("watched_cnt", 3.0)))
        + F.col("search_cnt") * F.lit(float(weights.get("search_cnt", 1.0)))
        + F.col("login_cnt") * F.lit(float(weights.get("login_cnt", 0.5)))
        + F.col("favorite_folder_action_cnt") * F.lit(float(weights.get("favorite_folder_action_cnt", 1.5)))
    )

    high_threshold = float(thresholds.get("high", 20.0))
    medium_threshold = float(thresholds.get("medium", 5.0))

    return (
        base_df.withColumn("activity_score_value", activity_score_expr.cast("double"))
        .withColumn("activity_score", F.round(F.col("activity_score_value"), 4).cast("decimal(18,4)"))
        .withColumn(
            "activity_level",
            F.when(F.col("activity_score_value") >= F.lit(high_threshold), F.lit("HIGH"))
            .when(F.col("activity_score_value") >= F.lit(medium_threshold), F.lit("MEDIUM"))
            .when(F.col("activity_score_value") > F.lit(0.0), F.lit("LOW"))
            .otherwise(F.lit("DORMANT")),
        )
        .drop("activity_score_value")
        .where(F.col("user_id").isNotNull())
        .select(
            "user_id",
            "user_nickname",
            "user_avatar",
            "user_url",
            "user_role",
            "user_status",
            "has_email",
            "email_domain",
            "register_date",
            "update_date",
            "register_age_days",
            "view_cnt",
            "rating_cnt",
            "rating_avg",
            "comment_cnt",
            "comment_like_cnt",
            "favorite_add_cnt",
            "favorite_remove_cnt",
            "watched_cnt",
            "search_cnt",
            "login_cnt",
            "favorite_folder_action_cnt",
            "active_movie_cnt",
            "activity_score",
            "activity_level",
            "is_active_today",
            "last_event_ts",
            "source_snapshot_dt",
        )
    )


def build_movie_profile(
    movie_snapshot_df: DataFrame,
    movie_action_df: DataFrame,
    thresholds: dict[str, Any],
) -> DataFrame:
    snapshot_df = movie_snapshot_df.alias("s")
    action_df = movie_action_df.alias("a")

    joined_df = snapshot_df.join(action_df, F.col("s.movie_id") == F.col("a.movie_id"), "full")

    base_df = joined_df.select(
        F.coalesce(F.col("s.movie_id"), F.col("a.movie_id")).cast("bigint").alias("movie_id"),
        F.coalesce(F.col("s.movie_name"), F.col("a.movie_name")).alias("movie_name"),
        F.col("s.movie_alias").alias("movie_alias"),
        F.coalesce(F.col("s.movie_year"), F.col("a.movie_year")).cast("int").alias("movie_year"),
        F.coalesce(F.col("s.movie_genres"), F.col("a.movie_genres")).alias("movie_genres"),
        F.col("s.movie_directors").alias("movie_directors"),
        F.col("s.movie_actors").alias("movie_actors"),
        F.col("s.movie_regions").alias("movie_regions"),
        F.col("s.movie_languages").alias("movie_languages"),
        F.col("s.movie_duration_mins").alias("movie_duration_mins"),
        F.col("s.release_date").alias("release_date"),
        F.col("s.movie_cover").alias("movie_cover"),
        F.coalesce(F.col("s.movie_score"), F.col("a.movie_score")).cast("decimal(3,1)").alias("movie_score"),
        F.coalesce(F.col("s.movie_douban_score"), F.col("a.movie_douban_score"))
        .cast("decimal(3,1)")
        .alias("movie_douban_score"),
        F.col("s.movie_votes").cast("int").alias("movie_votes"),
        F.col("s.movie_douban_votes").cast("int").alias("movie_douban_votes"),
        coalesce_metric("a.view_pv", "bigint").alias("view_pv"),
        coalesce_metric("a.view_uv", "bigint").alias("view_uv"),
        coalesce_metric("a.rating_cnt", "bigint").alias("rating_cnt"),
        F.col("a.rating_avg").cast("decimal(10,2)").alias("rating_avg"),
        coalesce_metric("a.comment_cnt", "bigint").alias("comment_cnt"),
        coalesce_metric("a.comment_like_cnt", "bigint").alias("comment_like_cnt"),
        coalesce_metric("a.favorite_add_cnt", "bigint").alias("favorite_add_cnt"),
        coalesce_metric("a.favorite_remove_cnt", "bigint").alias("favorite_remove_cnt"),
        coalesce_metric("a.watched_cnt", "bigint").alias("watched_cnt"),
        coalesce_metric("a.active_user_cnt", "bigint").alias("active_user_cnt"),
        F.coalesce(F.col("a.hot_score").cast("double"), F.lit(0.0)).alias("hot_score_value"),
        F.col("a.last_event_ts").alias("last_event_ts"),
        F.when(F.col("a.last_event_ts").isNotNull(), F.lit(1)).otherwise(F.lit(0)).cast("tinyint").alias("is_active_today"),
        F.col("s.source_snapshot_dt").alias("source_snapshot_dt"),
    )

    hot_threshold = float(thresholds.get("hot", 1000.0))
    warm_threshold = float(thresholds.get("warm", 100.0))
    engagement_numerator = (
        F.col("rating_cnt") + F.col("comment_cnt") + F.col("favorite_add_cnt") + F.col("watched_cnt")
    ).cast("double")

    return (
        base_df.withColumn("hot_score", F.round(F.col("hot_score_value"), 4).cast("decimal(18,4)"))
        .withColumn(
            "engagement_rate",
            F.when(F.col("view_uv") > 0, F.round(engagement_numerator / F.col("view_uv"), 4))
            .otherwise(F.lit(0.0))
            .cast("decimal(10,4)"),
        )
        .withColumn(
            "popularity_level",
            F.when(F.col("hot_score_value") >= F.lit(hot_threshold), F.lit("HOT"))
            .when(F.col("hot_score_value") >= F.lit(warm_threshold), F.lit("WARM"))
            .when(F.col("hot_score_value") > F.lit(0.0), F.lit("LONG_TAIL"))
            .otherwise(F.lit("QUIET")),
        )
        .drop("hot_score_value")
        .where(F.col("movie_id").isNotNull())
        .select(
            "movie_id",
            "movie_name",
            "movie_alias",
            "movie_year",
            "movie_genres",
            "movie_directors",
            "movie_actors",
            "movie_regions",
            "movie_languages",
            "movie_duration_mins",
            "release_date",
            "movie_cover",
            "movie_score",
            "movie_douban_score",
            "movie_votes",
            "movie_douban_votes",
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
            "engagement_rate",
            "popularity_level",
            "is_active_today",
            "last_event_ts",
            "source_snapshot_dt",
        )
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    profiles_config = merge_nested_dict(DEFAULT_DWS_PROFILES_CONFIG, config.get("dws_profiles", {}))

    calc_date = args.calc_date
    requested_snapshot_date = args.snapshot_date.strip()

    spark = build_spark_session("movie-dws-user-movie-profiles-1d", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS dws")

        source_tables = profiles_config["source_tables"]
        snapshot_date = resolve_common_dt_partition_date(
            [source_tables["user_snapshot"], source_tables["movie_snapshot"]],
            requested_snapshot_date,
            spark,
            fallback_max_date=calc_date,
        )
        user_snapshot_df = load_partition(spark, source_tables["user_snapshot"], snapshot_date)
        assert_non_empty_partition(user_snapshot_df, source_tables["user_snapshot"], {"dt": snapshot_date})
        movie_snapshot_df = load_partition(spark, source_tables["movie_snapshot"], snapshot_date)
        assert_non_empty_partition(movie_snapshot_df, source_tables["movie_snapshot"], {"dt": snapshot_date})
        user_action_df = load_partition(spark, source_tables["user_action"], calc_date)
        movie_action_df = load_partition(spark, source_tables["movie_action"], calc_date)

        user_profile_df = build_user_profile(
            user_snapshot_df=user_snapshot_df,
            user_action_df=user_action_df,
            calc_date=calc_date,
            weights=profiles_config.get("user_activity_score_weights", {}),
            thresholds=profiles_config.get("user_activity_level_thresholds", {}),
        )
        movie_profile_df = build_movie_profile(
            movie_snapshot_df=movie_snapshot_df,
            movie_action_df=movie_action_df,
            thresholds=profiles_config.get("movie_popularity_level_thresholds", {}),
        )

        write_partition(
            user_profile_df,
            profiles_config["target_tables"]["user_profile_1d"],
            profiles_config["sink_paths"]["user_profile_1d"],
            calc_date,
            spark,
        )
        write_partition(
            movie_profile_df,
            profiles_config["target_tables"]["movie_profile_1d"],
            profiles_config["sink_paths"]["movie_profile_1d"],
            calc_date,
            spark,
        )

        print(
            "DWS profile build finished. "
            f"user_target={profiles_config['target_tables']['user_profile_1d']}, "
            f"movie_target={profiles_config['target_tables']['movie_profile_1d']}, "
            f"dt={calc_date}, source_snapshot_dt={snapshot_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
