"""Build ADS daily user-behavior Sankey link data from DWD wide events.

Combines search-funnel and user-funnel into a single Sankey graph.
Each output row is one directed link: (source_node, target_node, user_count).

Sankey node layout
==================
                           ┌─> 搜索后浏览 ─┐              ┌─> 看过
  活跃用户 ─┬─> 搜索用户 ─┤               ├─> 浏览 ─┬─> 评分
            │              └─> 搜索未转化   │          ├─> 评论
            └─> 直接浏览 ──────────────────┘          ├─> 收藏
                                                       └─> 浏览流失
  看过/评分/评论/收藏 are parallel (non-sequential) engagement actions.
  Drop-off links are emitted for 浏览 and search stages.
"""
from __future__ import annotations

import argparse
import datetime as dt
from typing import Any

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build ADS daily user-behavior Sankey links from DWD wide events."
    )
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    return parser.parse_args()


def _user_flags(events_df: DataFrame) -> DataFrame:
    """Aggregate per-user boolean flags from the DWD wide event table."""
    return (
        events_df.where(F.col("user_id").isNotNull())
        .withColumn(
            "operation_norm",
            F.upper(F.trim(F.coalesce(F.col("operation_norm"), F.col("operation"), F.lit("")))),
        )
        .groupBy("user_id")
        .agg(
            F.max(F.coalesce(F.col("is_search"), F.lit(0))).cast("int").alias("did_search"),
            F.max(F.coalesce(F.col("is_view"), F.lit(0))).cast("int").alias("did_view"),
            F.max(F.coalesce(F.col("is_watched"), F.lit(0))).cast("int").alias("did_watched"),
            F.max(F.coalesce(F.col("is_rating"), F.lit(0))).cast("int").alias("did_rating"),
            F.max(F.coalesce(F.col("is_comment"), F.lit(0))).cast("int").alias("did_comment"),
            F.max(
                F.when(
                    (F.coalesce(F.col("is_favorite"), F.lit(0)) == 1)
                    & (F.col("operation_norm") == F.lit("ADD")),
                    F.lit(1),
                ).otherwise(F.lit(0))
            )
            .cast("int")
            .alias("did_favorite"),
        )
    )


def _search_view_flag(events_df: DataFrame) -> DataFrame:
    """For users who searched, determine whether they viewed *after* their first search."""
    search_events = events_df.where(
        (F.col("is_search") == 1)
        & F.col("user_id").isNotNull()
        & F.col("event_ts").isNotNull()
    )
    first_search = search_events.groupBy("user_id").agg(F.min("event_ts").alias("first_search_ts"))

    post_search_view = (
        first_search.join(
            events_df.where(
                (F.coalesce(F.col("is_view"), F.lit(0)) == 1)
                & F.col("user_id").isNotNull()
                & F.col("event_ts").isNotNull()
            ),
            on="user_id",
            how="inner",
        )
        .where(F.col("event_ts") > F.col("first_search_ts"))
        .select("user_id")
        .distinct()
        .withColumn("did_search_then_view", F.lit(1))
    )
    return post_search_view


def build_sankey_links(events_df: DataFrame, spark: SparkSession) -> DataFrame:
    """Return a DataFrame[source_node STRING, target_node STRING, user_count BIGINT]."""
    user_df = _user_flags(events_df).cache()
    search_view_df = _search_view_flag(events_df)

    # Join search-then-view flag
    enriched = user_df.join(search_view_df, on="user_id", how="left").fillna(
        0, subset=["did_search_then_view"]
    )

    total = enriched.count()
    if total == 0:
        schema = "source_node string, target_node string, user_count bigint"
        user_df.unpersist()
        return spark.createDataFrame([], schema)

    # ── Compute link counts ──────────────────────────────────────────────

    # Layer 1: 活跃用户 → 搜索用户 / 直接浏览
    search_users = enriched.where(F.col("did_search") == 1).count()
    direct_view_users = enriched.where(
        (F.col("did_search") == 0) & (F.col("did_view") == 1)
    ).count()
    active_no_action = total - search_users - direct_view_users
    # users who neither searched nor viewed
    if active_no_action < 0:
        active_no_action = 0

    # Layer 2: 搜索用户 → 搜索后浏览 / 搜索未转化
    search_then_view = enriched.where(
        (F.col("did_search") == 1) & (F.col("did_search_then_view") == 1)
    ).count()
    search_no_convert = search_users - search_then_view
    if search_no_convert < 0:
        search_no_convert = 0

    # Layer 4: 浏览 → parallel engagement actions (non-sequential)
    # Each action is independent; a user who viewed may do any combination.
    view_and_watched = enriched.where(
        (F.col("did_view") == 1) & (F.col("did_watched") == 1)
    ).count()
    view_and_rating = enriched.where(
        (F.col("did_view") == 1) & (F.col("did_rating") == 1)
    ).count()
    view_and_comment = enriched.where(
        (F.col("did_view") == 1) & (F.col("did_comment") == 1)
    ).count()
    view_and_favorite = enriched.where(
        (F.col("did_view") == 1) & (F.col("did_favorite") == 1)
    ).count()
    # Users who viewed but performed none of the four engagement actions
    view_only = enriched.where(
        (F.col("did_view") == 1)
        & (F.col("did_watched") == 0)
        & (F.col("did_rating") == 0)
        & (F.col("did_comment") == 0)
        & (F.col("did_favorite") == 0)
    ).count()

    user_df.unpersist()

    # ── Assemble links ───────────────────────────────────────────────────
    links: list[tuple[str, str, int]] = []

    def _add(src: str, tgt: str, cnt: int) -> None:
        if cnt > 0:
            links.append((src, tgt, cnt))

    # Layer 1
    _add("活跃用户", "搜索用户", search_users)
    _add("活跃用户", "直接浏览", direct_view_users)
    _add("活跃用户", "未行动流失", active_no_action)

    # Layer 2
    _add("搜索用户", "搜索后浏览", search_then_view)
    _add("搜索用户", "搜索未转化", search_no_convert)

    # Layer 3: merge into 浏览
    _add("搜索后浏览", "浏览", search_then_view)
    _add("直接浏览", "浏览", direct_view_users)

    # Layer 4: parallel engagement branches from 浏览
    _add("浏览", "看过", view_and_watched)
    _add("浏览", "评分", view_and_rating)
    _add("浏览", "评论", view_and_comment)
    _add("浏览", "收藏", view_and_favorite)
    _add("浏览", "浏览流失", view_only)

    return spark.createDataFrame(
        links, schema="source_node string, target_node string, user_count bigint"
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    ads_config: dict[str, Any] = config["ads_user_behavior_sankey"]

    calc_date = args.calc_date
    source_table = ads_config["source_table"]
    target_table = ads_config["target_table"]
    sink_path = ads_config["sink_path"]

    spark = build_spark_session("movie-ads-user-behavior-sankey-1d", spark_config)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        source_df = spark.table(source_table).where(F.col("dt") == calc_date)
        result_df = build_sankey_links(source_df, spark)
        write_partition(result_df, target_table, sink_path, calc_date, spark)

        print(
            f"ADS user-behavior Sankey build finished. "
            f"source={source_table}, target={target_table}, dt={calc_date}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
