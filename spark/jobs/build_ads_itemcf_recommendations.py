from __future__ import annotations

import argparse
import datetime as dt
import uuid
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import Window
from pyspark.sql import functions as F
from pyspark.sql.utils import AnalysisException

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build ItemCF similarity in ADS.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--calc-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Calculation date in format YYYY-MM-DD.",
    )
    parser.add_argument("--top-k", type=int, default=0, help="Override top K similar items per movie. 0 means use config.")
    return parser.parse_args()


def resolve_positive_int(raw_value: Any, name: str) -> int:
    value = int(raw_value)
    if value <= 0:
        raise ValueError(f"Invalid {name}: {raw_value}")
    return value


def build_user_item_preference(events_df: DataFrame, weights: dict[str, Any], min_score: float) -> DataFrame:
    rating_value = F.coalesce(F.col("rating"), F.col("rating_snapshot")).cast("double")
    operation_norm = F.upper(F.trim(F.coalesce(F.col("operation_norm"), F.col("operation"))))
    favorite_add_weight = float(weights.get("favorite_add", weights.get("favorite", 2.0)))
    favorite_remove_weight = float(weights.get("favorite_remove", -1.0))
    event_score_expr = (
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

    return (
        events_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .withColumn("event_score", event_score_expr.cast("double"))
        .groupBy("user_id", "movie_id")
        .agg(
            F.sum("event_score").cast("double").alias("preference_score"),
            F.max("event_ts").alias("last_event_ts"),
        )
        .where(F.col("preference_score") >= F.lit(float(min_score)))
    )


def load_user_item_preference_snapshot(source_df: DataFrame, min_score: float) -> DataFrame:
    return (
        source_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("preference_score").cast("double").alias("preference_score"),
            F.col("last_event_ts").alias("last_event_ts"),
        )
        .where(F.col("preference_score") >= F.lit(float(min_score)))
    )


def build_item_similarity(
    user_item_df: DataFrame,
    min_co_users: int,
    shrinkage: float,
    top_k: int,
    max_items_per_user: int = 200,
) -> DataFrame:
    # Cap items per user to avoid O(n^2) explosion in self-join
    user_rank_window = Window.partitionBy("user_id").orderBy(
        F.col("preference_score").desc(), F.col("movie_id").asc()
    )
    capped_df = (
        user_item_df.withColumn("_user_item_rank", F.row_number().over(user_rank_window))
        .where(F.col("_user_item_rank") <= F.lit(int(max_items_per_user)))
        .drop("_user_item_rank")
    )

    left_df = capped_df.select(
        F.col("user_id"),
        F.col("movie_id").alias("movie_id_left"),
        F.col("preference_score").alias("score_left"),
    )
    right_df = capped_df.select(
        F.col("user_id"),
        F.col("movie_id").alias("movie_id_right"),
        F.col("preference_score").alias("score_right"),
    )

    pair_df = (
        left_df.join(right_df, on="user_id", how="inner")
        .where(F.col("movie_id_left") < F.col("movie_id_right"))
        .groupBy("movie_id_left", "movie_id_right")
        .agg(
            F.sum(F.col("score_left") * F.col("score_right")).cast("double").alias("dot_product"),
            F.count(F.lit(1)).cast("bigint").alias("common_user_cnt"),
        )
        .where(F.col("common_user_cnt") >= F.lit(int(min_co_users)))
    )

    norm_df = user_item_df.groupBy("movie_id").agg(
        F.sqrt(F.sum(F.col("preference_score") * F.col("preference_score"))).cast("double").alias("item_norm"),
        F.count(F.lit(1)).cast("bigint").alias("item_user_cnt"),
    )

    shrinkage_lit = F.lit(float(shrinkage))
    similarity_pair_df = (
        pair_df.join(
            norm_df.select(
                F.col("movie_id").alias("movie_id_left"),
                F.col("item_norm").alias("norm_left"),
                F.col("item_user_cnt").alias("movie_user_cnt"),
            ),
            on="movie_id_left",
            how="inner",
        )
        .join(
            norm_df.select(
                F.col("movie_id").alias("movie_id_right"),
                F.col("item_norm").alias("norm_right"),
                F.col("item_user_cnt").alias("similar_movie_user_cnt"),
            ),
            on="movie_id_right",
            how="inner",
        )
        .withColumn(
            "raw_similarity",
            F.when(
                (F.col("norm_left") > 0.0) & (F.col("norm_right") > 0.0),
                F.col("dot_product") / (F.col("norm_left") * F.col("norm_right")),
            ).otherwise(F.lit(0.0)),
        )
        .withColumn(
            "similarity_score",
            F.col("raw_similarity") * (F.col("common_user_cnt") / (F.col("common_user_cnt") + shrinkage_lit)),
        )
        .where(F.col("similarity_score") > F.lit(0.0))
    )

    directed_df = similarity_pair_df.select(
        F.col("movie_id_left").alias("movie_id"),
        F.col("movie_id_right").alias("similar_movie_id"),
        F.col("similarity_score"),
        F.col("common_user_cnt"),
        F.col("movie_user_cnt"),
        F.col("similar_movie_user_cnt"),
    ).unionByName(
        similarity_pair_df.select(
            F.col("movie_id_right").alias("movie_id"),
            F.col("movie_id_left").alias("similar_movie_id"),
            F.col("similarity_score"),
            F.col("common_user_cnt"),
            F.col("similar_movie_user_cnt").alias("movie_user_cnt"),
            F.col("movie_user_cnt").alias("similar_movie_user_cnt"),
        )
    )

    rank_window = Window.partitionBy("movie_id").orderBy(
        F.col("similarity_score").desc(),
        F.col("common_user_cnt").desc(),
        F.col("similar_movie_id").asc(),
    )
    return (
        directed_df.withColumn("rank_no", F.row_number().over(rank_window))
        .where(F.col("rank_no") <= F.lit(int(top_k)))
        .select(
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("similar_movie_id").cast("bigint").alias("similar_movie_id"),
            F.col("rank_no").cast("int").alias("rank_no"),
            F.round(F.col("similarity_score"), 8).cast("decimal(18,8)").alias("similarity_score"),
            F.col("common_user_cnt").cast("bigint").alias("common_user_cnt"),
            F.col("movie_user_cnt").cast("bigint").alias("movie_user_cnt"),
            F.col("similar_movie_user_cnt").cast("bigint").alias("similar_movie_user_cnt"),
            F.lit(2).cast("tinyint").alias("similarity_type"),
        )
    )


def align_similarity_schema(df: DataFrame) -> DataFrame:
    return df.select(
        F.col("movie_id").cast("bigint").alias("movie_id"),
        F.col("similar_movie_id").cast("bigint").alias("similar_movie_id"),
        F.col("rank_no").cast("int").alias("rank_no"),
        F.col("similarity_score").cast("decimal(18,8)").alias("similarity_score"),
        F.col("common_user_cnt").cast("bigint").alias("common_user_cnt"),
        F.col("movie_user_cnt").cast("bigint").alias("movie_user_cnt"),
        F.col("similar_movie_user_cnt").cast("bigint").alias("similar_movie_user_cnt"),
        F.col("similarity_type").cast("tinyint").alias("similarity_type"),
    )


def merge_with_existing_partition(
    spark: Any,
    target_table: str,
    calc_date: str,
    current_similarity_type: int,
    new_similarity_df: DataFrame,
) -> DataFrame:
    try:
        existing_df = spark.table(target_table).where(
            (F.col("dt") == F.lit(calc_date))
            & (F.col("similarity_type").cast("int") != F.lit(int(current_similarity_type)))
        )
    except AnalysisException:
        return new_similarity_df

    return align_similarity_schema(existing_df).unionByName(align_similarity_schema(new_similarity_df))


def build_staging_path(sink_path: str, calc_date: str, similarity_type: int) -> str:
    run_id = uuid.uuid4().hex
    return (
        f"{sink_path.rstrip('/')}_staging/"
        f"ads_itemcf_similar_movies/dt={calc_date}/similarity_type={int(similarity_type)}/run_id={run_id}"
    )


def delete_path_if_exists(spark: Any, path: str) -> None:
    jvm = spark._jvm
    hadoop_conf = spark._jsc.hadoopConfiguration()
    path_obj = jvm.org.apache.hadoop.fs.Path(path)
    filesystem = path_obj.getFileSystem(hadoop_conf)
    if filesystem.exists(path_obj):
        filesystem.delete(path_obj, True)


def stage_output_for_overwrite(output_df: DataFrame, staging_path: str, spark: Any) -> DataFrame:
    delete_path_if_exists(spark, staging_path)
    output_df.write.mode("overwrite").format("orc").save(staging_path)
    return spark.read.format("orc").load(staging_path)


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    itemcf_config: dict[str, Any] = config["ads_itemcf"]

    calc_date = args.calc_date
    lookback_days = resolve_positive_int(itemcf_config.get("lookback_days", 90), "lookback_days")
    top_k = args.top_k if args.top_k > 0 else resolve_positive_int(itemcf_config.get("top_k_similar", 100), "top_k_similar")

    min_user_item_score = float(itemcf_config.get("min_user_item_score", 0.1))
    min_co_users = resolve_positive_int(itemcf_config.get("min_co_users", 2), "min_co_users")
    shrinkage = float(itemcf_config.get("shrinkage", 10.0))
    if shrinkage < 0.0:
        raise ValueError(f"Invalid shrinkage: {shrinkage}")

    source_table = itemcf_config["source_table"]
    target_table = itemcf_config["target_table"]
    sink_path = itemcf_config["sink_path"]
    weights = itemcf_config.get("event_score_weights", {})
    source_type = str(itemcf_config.get("source_type", "user_item_preference")).strip().lower()
    if source_type not in {"user_item_preference", "event_wide"}:
        raise ValueError(f"Invalid source_type: {source_type}. Supported values: user_item_preference, event_wide")
    similarity_type = 2

    spark = build_spark_session("movie-ads-itemcf-similarity", spark_config)
    similarity_staging_path = build_staging_path(
        sink_path,
        calc_date=calc_date,
        similarity_type=similarity_type,
    )
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        max_items_per_user = resolve_positive_int(itemcf_config.get("max_items_per_user", 200), "max_items_per_user")
        if source_type == "user_item_preference":
            source_df = spark.table(source_table).where(F.col("dt") == calc_date)
            user_item_df = load_user_item_preference_snapshot(source_df, min_user_item_score).cache()
            source_scope = f"snapshot_dt={calc_date}"
        else:
            start_date = (dt.date.fromisoformat(calc_date) - dt.timedelta(days=lookback_days - 1)).isoformat()
            source_df = spark.table(source_table).where((F.col("dt") >= start_date) & (F.col("dt") <= calc_date))
            user_item_df = build_user_item_preference(source_df, weights, min_user_item_score).cache()
            source_scope = f"dt_range=[{start_date}, {calc_date}]"

        similarity_df = build_item_similarity(
            user_item_df, min_co_users=min_co_users, shrinkage=shrinkage,
            top_k=top_k, max_items_per_user=max_items_per_user,
        ).cache()

        merged_similarity_df = merge_with_existing_partition(
            spark,
            target_table=target_table,
            calc_date=calc_date,
            current_similarity_type=similarity_type,
            new_similarity_df=similarity_df,
        )
        staged_similarity_df = stage_output_for_overwrite(
            merged_similarity_df,
            staging_path=similarity_staging_path,
            spark=spark,
        )

        write_partition(
            staged_similarity_df,
            target_table,
            sink_path,
            calc_date,
            spark,
        )

        similarity_count = similarity_df.count()

        similarity_df.unpersist()
        user_item_df.unpersist()
        print(
            "ADS itemCF build finished. "
            f"source={source_table}, source_type={source_type}, source_scope={source_scope}, dt={calc_date}, "
            f"top_k={top_k}, similar_cnt={similarity_count}"
        )
    finally:
        try:
            delete_path_if_exists(spark, similarity_staging_path)
        except Exception as exc:  # pragma: no cover - cleanup should not mask job failure
            print(f"Failed to cleanup ItemCF staging path: path={similarity_staging_path}, error={exc}")
        finally:
            spark.stop()


if __name__ == "__main__":
    run()
