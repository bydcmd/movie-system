from __future__ import annotations

import argparse
import datetime as dt
import uuid
from typing import Any

from pyspark.ml.recommendation import ALS
from pyspark.sql import DataFrame, Window
from pyspark.sql import functions as F
from pyspark.sql.utils import AnalysisException

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build ALS-based similar movies in ADS.")
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


def resolve_non_negative_float(raw_value: Any, name: str) -> float:
    value = float(raw_value)
    if value < 0.0:
        raise ValueError(f"Invalid {name}: {raw_value}")
    return value


def load_user_item_preference_snapshot(source_df: DataFrame) -> DataFrame:
    return (
        source_df.where(F.col("user_id").isNotNull() & F.col("movie_id").isNotNull())
        .select(
            F.col("user_id").cast("string").alias("user_id"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.coalesce(F.col("view_cnt"), F.lit(0)).cast("bigint").alias("view_cnt"),
            F.coalesce(F.col("rating_cnt"), F.lit(0)).cast("bigint").alias("rating_cnt"),
            F.col("rating_avg").cast("double").alias("rating_avg"),
            F.coalesce(F.col("comment_cnt"), F.lit(0)).cast("bigint").alias("comment_cnt"),
            F.coalesce(F.col("favorite_add_cnt"), F.lit(0)).cast("bigint").alias("favorite_add_cnt"),
            F.coalesce(F.col("favorite_remove_cnt"), F.lit(0)).cast("bigint").alias("favorite_remove_cnt"),
            F.coalesce(F.col("watched_cnt"), F.lit(0)).cast("bigint").alias("watched_cnt"),
            F.col("preference_score").cast("double").alias("preference_score"),
            F.col("last_event_ts").alias("last_event_ts"),
        )
    )


def build_positive_score_column(weights: dict[str, Any]) -> Any:
    rating_avg_bonus = float(weights.get("rating_avg_bonus", 0.0))
    return (
        F.col("view_cnt") * F.lit(float(weights.get("view_cnt", 0.2)))
        + F.col("rating_cnt") * F.lit(float(weights.get("rating_cnt", 1.0)))
        + F.col("comment_cnt") * F.lit(float(weights.get("comment_cnt", 1.0)))
        + F.col("favorite_add_cnt") * F.lit(float(weights.get("favorite_add_cnt", 2.0)))
        + F.col("watched_cnt") * F.lit(float(weights.get("watched_cnt", 3.0)))
        + F.when(
            (F.col("rating_cnt") > 0) & F.col("rating_avg").isNotNull(),
            (F.col("rating_avg") / F.lit(5.0)) * F.lit(rating_avg_bonus),
        ).otherwise(F.lit(0.0))
    ).cast("double")


def apply_score_transform(raw_score_col: Any, transform_config: dict[str, Any]) -> Any:
    method = str(transform_config.get("method", "log1p")).strip().lower()
    scale = resolve_non_negative_float(transform_config.get("scale", 1.0), "score_transform.scale")
    scaled_col = raw_score_col * F.lit(scale)

    if method == "log1p":
        return F.log1p(scaled_col)
    if method == "identity":
        return scaled_col
    raise ValueError(f"Unsupported score transform method: {method}")


def build_positive_training_frame(
    source_df: DataFrame,
    positive_score_weights: dict[str, Any],
    score_transform_config: dict[str, Any],
    min_user_positive_items: int,
    min_item_positive_users: int,
) -> tuple[DataFrame, DataFrame]:
    positive_df = (
        load_user_item_preference_snapshot(source_df)
        .withColumn("raw_positive_score", build_positive_score_column(positive_score_weights))
        .withColumn("als_score", apply_score_transform(F.col("raw_positive_score"), score_transform_config))
        .where(F.col("raw_positive_score") > F.lit(0.0))
        .where(F.col("als_score") > F.lit(0.0))
        .select("user_id", "movie_id", "als_score")
        .dropDuplicates(["user_id", "movie_id"])
    )

    filtered_df = positive_df
    for _ in range(2):
        user_support_df = (
            filtered_df.groupBy("user_id")
            .agg(F.count(F.lit(1)).cast("bigint").alias("positive_item_cnt"))
            .where(F.col("positive_item_cnt") >= F.lit(int(min_user_positive_items)))
            .select("user_id")
        )
        filtered_df = filtered_df.join(user_support_df, on="user_id", how="inner")

        item_support_df = (
            filtered_df.groupBy("movie_id")
            .agg(F.count(F.lit(1)).cast("bigint").alias("movie_user_cnt"))
            .where(F.col("movie_user_cnt") >= F.lit(int(min_item_positive_users)))
            .select("movie_id")
        )
        filtered_df = filtered_df.join(item_support_df, on="movie_id", how="inner")

    movie_support_df = filtered_df.groupBy("movie_id").agg(
        F.count(F.lit(1)).cast("bigint").alias("movie_user_cnt")
    )
    return filtered_df, movie_support_df


def build_index_frame(source_df: DataFrame, key_column: str, index_column: str) -> DataFrame:
    index_window = Window.orderBy(F.col(key_column).asc())
    return (
        source_df.select(key_column)
        .distinct()
        .withColumn(index_column, (F.row_number().over(index_window) - F.lit(1)).cast("int"))
    )


def build_als_training_matrix(
    training_df: DataFrame,
) -> tuple[DataFrame, DataFrame, DataFrame]:
    user_index_df = build_index_frame(training_df, "user_id", "user_idx")
    item_index_df = build_index_frame(training_df, "movie_id", "item_idx")

    indexed_df = (
        training_df.join(user_index_df, on="user_id", how="inner")
        .join(item_index_df, on="movie_id", how="inner")
        .select(
            F.col("user_idx").cast("int").alias("user_idx"),
            F.col("item_idx").cast("int").alias("item_idx"),
            F.col("als_score").cast("float").alias("als_score"),
        )
    )
    return indexed_df, user_index_df, item_index_df


def train_als_model(training_matrix_df: DataFrame, als_params: dict[str, Any]) -> Any:
    als = ALS(
        userCol="user_idx",
        itemCol="item_idx",
        ratingCol="als_score",
        implicitPrefs=True,
        rank=resolve_positive_int(als_params.get("rank", 64), "als_params.rank"),
        maxIter=resolve_positive_int(als_params.get("max_iter", 10), "als_params.max_iter"),
        regParam=resolve_non_negative_float(als_params.get("reg_param", 0.05), "als_params.reg_param"),
        alpha=resolve_non_negative_float(als_params.get("alpha", 30.0), "als_params.alpha"),
        coldStartStrategy="drop",
        seed=int(als_params.get("seed", 20260329)),
    )
    return als.fit(training_matrix_df)


def build_item_factor_frame(
    model: Any,
    item_index_df: DataFrame,
    movie_support_df: DataFrame,
) -> DataFrame:
    feature_norm_expr = "sqrt(aggregate(transform(features, x -> x * x), cast(0.0 as double), (acc, x) -> acc + x))"
    normalized_features_expr = "transform(features, x -> x / feature_norm)"

    factor_df = (
        model.itemFactors.select(
            F.col("id").cast("int").alias("item_idx"),
            F.expr("transform(features, x -> cast(x as double))").alias("features"),
        )
        .withColumn("feature_norm", F.expr(feature_norm_expr))
        .where(F.col("feature_norm") > F.lit(0.0))
        .withColumn("normalized_features", F.expr(normalized_features_expr))
        .drop("features", "feature_norm")
    )

    return (
        factor_df.join(item_index_df, on="item_idx", how="inner")
        .join(movie_support_df, on="movie_id", how="inner")
        .select(
            F.col("item_idx").cast("int").alias("item_idx"),
            F.col("movie_id").cast("bigint").alias("movie_id"),
            F.col("movie_user_cnt").cast("bigint").alias("movie_user_cnt"),
            F.col("normalized_features").alias("normalized_features"),
        )
    )


def build_similarity_frame(
    item_factor_df: DataFrame,
    top_k: int,
    min_similarity_score: float,
    similarity_type: int,
    block_count: int,
) -> DataFrame:
    spark = item_factor_df.sparkSession
    effective_block_count = max(int(block_count), 1)
    block_pairs = [(left_block_id, right_block_id) for left_block_id in range(effective_block_count) for right_block_id in range(left_block_id, effective_block_count)]
    block_pair_df = spark.createDataFrame(block_pairs, ["left_block_id", "right_block_id"])

    blocked_df = item_factor_df.withColumn(
        "block_id",
        F.pmod(F.col("item_idx"), F.lit(effective_block_count)).cast("int"),
    ).cache()

    left_df = blocked_df.select(
        F.col("block_id").alias("left_block_id"),
        F.col("item_idx").alias("item_idx_left"),
        F.col("movie_id").alias("movie_id_left"),
        F.col("movie_user_cnt").alias("movie_user_cnt"),
        F.col("normalized_features").alias("features_left"),
    )
    right_df = blocked_df.select(
        F.col("block_id").alias("right_block_id"),
        F.col("item_idx").alias("item_idx_right"),
        F.col("movie_id").alias("movie_id_right"),
        F.col("movie_user_cnt").alias("similar_movie_user_cnt"),
        F.col("normalized_features").alias("features_right"),
    )

    similarity_expr = (
        "aggregate("
        "zip_with(features_left, features_right, (x, y) -> x * y), "
        "cast(0.0 as double), "
        "(acc, value) -> acc + value"
        ")"
    )

    pair_df = (
        F.broadcast(block_pair_df).alias("bp")
        .join(left_df.alias("left"), F.col("bp.left_block_id") == F.col("left.left_block_id"), how="inner")
        .join(right_df.alias("right"), F.col("bp.right_block_id") == F.col("right.right_block_id"), how="inner")
        .where(
            (F.col("bp.left_block_id") < F.col("bp.right_block_id"))
            | (
                (F.col("bp.left_block_id") == F.col("bp.right_block_id"))
                & (F.col("left.item_idx_left") < F.col("right.item_idx_right"))
            )
        )
        .select(
            F.col("left.movie_id_left").alias("movie_id_left"),
            F.col("right.movie_id_right").alias("movie_id_right"),
            F.col("left.movie_user_cnt").alias("movie_user_cnt"),
            F.col("right.similar_movie_user_cnt").alias("similar_movie_user_cnt"),
            F.expr(similarity_expr).cast("double").alias("similarity_score"),
        )
        .where(F.col("similarity_score") > F.lit(float(min_similarity_score)))
    )

    blocked_df.unpersist()

    directed_df = pair_df.select(
        F.col("movie_id_left").alias("movie_id"),
        F.col("movie_id_right").alias("similar_movie_id"),
        F.col("similarity_score"),
        F.lit(0).cast("bigint").alias("common_user_cnt"),
        F.col("movie_user_cnt"),
        F.col("similar_movie_user_cnt"),
    ).unionByName(
        pair_df.select(
            F.col("movie_id_right").alias("movie_id"),
            F.col("movie_id_left").alias("similar_movie_id"),
            F.col("similarity_score"),
            F.lit(0).cast("bigint").alias("common_user_cnt"),
            F.col("similar_movie_user_cnt").alias("movie_user_cnt"),
            F.col("movie_user_cnt").alias("similar_movie_user_cnt"),
        )
    )

    rank_window = Window.partitionBy("movie_id").orderBy(
        F.col("similarity_score").desc(),
        F.col("similar_movie_user_cnt").desc(),
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
            F.lit(int(similarity_type)).cast("tinyint").alias("similarity_type"),
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
        f"ads_als_similar_movies/dt={calc_date}/similarity_type={int(similarity_type)}/run_id={run_id}"
    )


def delete_path_if_exists(spark: Any, path: str) -> None:
    jvm = spark._jvm
    hadoop_conf = spark._jsc.hadoopConfiguration()
    path_obj = jvm.org.apache.hadoop.fs.Path(path)
    filesystem = path_obj.getFileSystem(hadoop_conf)
    if filesystem.exists(path_obj):
        filesystem.delete(path_obj, True)


def stage_output_for_overwrite(output_df: DataFrame, staging_path: str, spark: Any) -> DataFrame:
    # Break the lineage from the target table before overwriting the target partition.
    delete_path_if_exists(spark, staging_path)
    output_df.write.mode("overwrite").format("orc").save(staging_path)
    return spark.read.format("orc").load(staging_path)


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    als_config: dict[str, Any] = config["ads_als_similar_movies"]

    calc_date = args.calc_date
    top_k = args.top_k if args.top_k > 0 else resolve_positive_int(
        als_config.get("top_k_similar", 50), "top_k_similar"
    )
    min_similarity_score = resolve_non_negative_float(
        als_config.get("min_similarity_score", 0.05), "min_similarity_score"
    )
    min_user_positive_items = resolve_positive_int(
        als_config.get("min_user_positive_items", 2), "min_user_positive_items"
    )
    min_item_positive_users = resolve_positive_int(
        als_config.get("min_item_positive_users", 5), "min_item_positive_users"
    )
    similarity_type = resolve_positive_int(als_config.get("similarity_type", 3), "similarity_type")
    source_table = als_config["source_table"]
    target_table = als_config["target_table"]
    sink_path = als_config["sink_path"]
    positive_score_weights = als_config.get("positive_score_weights", {})
    score_transform_config = als_config.get("score_transform", {})
    als_params = als_config.get("als_params", {})
    pairing_config = als_config.get("pairing", {})
    block_count = resolve_positive_int(pairing_config.get("block_count", 16), "pairing.block_count")

    spark = build_spark_session("movie-ads-als-similar-movies", spark_config)
    staging_path = build_staging_path(sink_path, calc_date, similarity_type)
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ads")

        source_df = spark.table(source_table).where(F.col("dt") == calc_date)
        training_df, movie_support_df = build_positive_training_frame(
            source_df,
            positive_score_weights=positive_score_weights,
            score_transform_config=score_transform_config,
            min_user_positive_items=min_user_positive_items,
            min_item_positive_users=min_item_positive_users,
        )
        training_df = training_df.cache()
        movie_support_df = movie_support_df.cache()

        if training_df.limit(1).count() == 0:
            raise ValueError(
                "No ALS training data after positive-score and support filtering. "
                f"source_table={source_table}, dt={calc_date}"
            )

        training_matrix_df, user_index_df, item_index_df = build_als_training_matrix(training_df)
        training_matrix_df = training_matrix_df.cache()

        model = train_als_model(training_matrix_df, als_params)

        item_factor_df = build_item_factor_frame(model, item_index_df, movie_support_df).cache()
        if item_factor_df.limit(1).count() == 0:
            raise ValueError(
                "ALS model produced no usable item factors. "
                f"source_table={source_table}, dt={calc_date}"
            )

        similarity_df = build_similarity_frame(
            item_factor_df,
            top_k=top_k,
            min_similarity_score=min_similarity_score,
            similarity_type=similarity_type,
            block_count=block_count,
        ).cache()

        output_df = merge_with_existing_partition(
            spark,
            target_table=target_table,
            calc_date=calc_date,
            current_similarity_type=similarity_type,
            new_similarity_df=similarity_df,
        )
        staged_output_df = stage_output_for_overwrite(output_df, staging_path=staging_path, spark=spark)

        write_partition(staged_output_df, target_table, sink_path, calc_date, spark)

        training_count = training_df.count()
        item_count = item_factor_df.count()
        similarity_count = similarity_df.count()

        similarity_df.unpersist()
        item_factor_df.unpersist()
        training_matrix_df.unpersist()
        movie_support_df.unpersist()
        training_df.unpersist()

        print(
            "ADS ALS similar movies build finished. "
            f"source={source_table}, dt={calc_date}, top_k={top_k}, "
            f"training_cnt={training_count}, item_factor_cnt={item_count}, similar_cnt={similarity_count}, "
            f"similarity_type={similarity_type}, min_similarity_score={min_similarity_score}"
        )
    finally:
        try:
            delete_path_if_exists(spark, staging_path)
        except Exception as exc:  # pragma: no cover - cleanup should not mask job failure
            print(f"Failed to cleanup ALS staging path: path={staging_path}, error={exc}")
        finally:
            spark.stop()


if __name__ == "__main__":
    run()
