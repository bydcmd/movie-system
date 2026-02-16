#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实时推荐 - PySpark Structured Streaming + Kafka + Redis

流程:
1) 从 Kafka 读取后端事件(EventEnvelope)
2) 依据事件类型映射为用户对电影的偏好权重
3) 通过 stats_similar_movies 扩展相似电影候选
4) 将用户-电影分数增量写入 Redis ZSET (recs:realtime:{userId})
"""

import argparse
from typing import List

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, from_json, when, lit, coalesce, sum as spark_sum
)
from pyspark.sql.types import (
    StructType, StructField, StringType, LongType, IntegerType, DoubleType
)
from pyspark.sql.window import Window
from pyspark.sql.functions import row_number


def parse_args():
    parser = argparse.ArgumentParser(description="实时推荐: PySpark + Kafka + Redis")
    # Kafka
    parser.add_argument("--kafka-bootstrap", required=True, help="Kafka bootstrap servers")
    parser.add_argument("--kafka-topics", default="movie-view-history,movie-rating-events,movie-comment-events,movie-favorite-events",
                        help="Kafka topics, comma-separated")
    parser.add_argument("--kafka-starting-offsets", default="latest",
                        choices=["latest", "earliest"], help="Kafka starting offsets")

    # MySQL (for similarity table)
    parser.add_argument("--jdbc-url", required=True, help="MySQL JDBC URL")
    parser.add_argument("--db-user", required=True, help="MySQL username")
    parser.add_argument("--db-password", required=True, help="MySQL password")

    # Redis
    parser.add_argument("--redis-host", required=True, help="Redis host")
    parser.add_argument("--redis-port", type=int, default=6379, help="Redis port")
    parser.add_argument("--redis-db", type=int, default=0, help="Redis DB index")
    parser.add_argument("--redis-password", default="", help="Redis password (optional)")
    parser.add_argument("--redis-key-prefix", default="recs:realtime:", help="Redis key prefix")
    parser.add_argument("--redis-ttl-hours", type=int, default=24, help="Redis key TTL in hours")

    # Recs
    parser.add_argument("--similarity-types", default="1,2",
                        help="Similarity types to use, comma-separated, e.g. 1,2")
    parser.add_argument("--max-similar-per-movie", type=int, default=50,
                        help="Max similar movies per source movie")
    parser.add_argument("--top-n", type=int, default=50,
                        help="Keep top N recommendations per user in Redis")

    # Weights
    parser.add_argument("--w-view", type=float, default=1.0, help="Weight for view_history")
    parser.add_argument("--w-rating", type=float, default=3.0, help="Weight for rating (scaled by normalized rating/5)")
    parser.add_argument("--w-favorite", type=float, default=5.0, help="Weight for favorite ADD")
    parser.add_argument("--w-comment", type=float, default=2.0, help="Weight for comment CREATE")

    # Streaming
    parser.add_argument("--checkpoint", required=True, help="Checkpoint location for streaming query")
    parser.add_argument("--shuffle-partitions", type=int, default=200, help="Spark shuffle partitions")

    return parser.parse_args()


def parse_similarity_types(value: str) -> List[int]:
    if not value:
        return []
    parts = [p.strip() for p in value.split(",") if p.strip()]
    result = []
    for p in parts:
        try:
            result.append(int(p))
        except ValueError:
            continue
    return result


def build_similarity_df(spark: SparkSession, jdbc_url: str, db_user: str, db_password: str,
                        similarity_types: List[int], max_similar_per_movie: int):
    df = spark.read \
        .format("jdbc") \
        .option("url", jdbc_url) \
        .option("dbtable", "stats_similar_movies") \
        .option("user", db_user) \
        .option("password", db_password) \
        .option("driver", "com.mysql.cj.jdbc.Driver") \
        .load() \
        .select("movie_id", "similar_movie_id", "similarity_score", "similarity_type")

    if similarity_types:
        df = df.filter(col("similarity_type").isin(similarity_types))

    if max_similar_per_movie and max_similar_per_movie > 0:
        w = Window.partitionBy("movie_id").orderBy(col("similarity_score").desc())
        df = df.withColumn("rn", row_number().over(w)) \
            .filter(col("rn") <= lit(max_similar_per_movie)) \
            .drop("rn")

    return df.select("movie_id", "similar_movie_id", "similarity_score")


def build_user_history_df(spark: SparkSession, jdbc_url: str, db_user: str, db_password: str):
    view_df = spark.read \
        .format("jdbc") \
        .option("url", jdbc_url) \
        .option("dbtable", "view_history") \
        .option("user", db_user) \
        .option("password", db_password) \
        .option("driver", "com.mysql.cj.jdbc.Driver") \
        .load() \
        .select(col("user_id"), col("movie_id"))

    rating_df = spark.read \
        .format("jdbc") \
        .option("url", jdbc_url) \
        .option("dbtable", "ratings") \
        .option("user", db_user) \
        .option("password", db_password) \
        .option("driver", "com.mysql.cj.jdbc.Driver") \
        .load() \
        .select(col("user_id"), col("movie_id"))

    return view_df.unionByName(rating_df).dropDuplicates(["user_id", "movie_id"])


def build_event_schema():
    data_schema = StructType([
        StructField("userId", StringType(), True),
        StructField("movieId", LongType(), True),
        StructField("rating", IntegerType(), True),
        StructField("operation", StringType(), True),
        StructField("ratingTime", StringType(), True),
        StructField("viewTime", LongType(), True),
        StructField("commentId", LongType(), True),
        StructField("type", IntegerType(), True),
        StructField("folderId", LongType(), True),
        StructField("contentLength", IntegerType(), True),
    ])
    schema = StructType([
        StructField("eventId", StringType(), True),
        StructField("eventType", StringType(), True),
        StructField("occurredAt", LongType(), True),
        StructField("data", data_schema, True)
    ])
    return schema


def build_spark(shuffle_partitions: int) -> SparkSession:
    return SparkSession.builder \
        .appName("MovieRealtimeRecs") \
        .config("spark.sql.shuffle.partitions", str(shuffle_partitions)) \
        .getOrCreate()


def main():
    args = parse_args()

    spark = build_spark(args.shuffle_partitions)
    spark.sparkContext.setLogLevel("WARN")

    similarity_types = parse_similarity_types(args.similarity_types)
    sim_df = build_similarity_df(
        spark, args.jdbc_url, args.db_user, args.db_password,
        similarity_types, args.max_similar_per_movie
    ).cache()
    history_df = build_user_history_df(
        spark, args.jdbc_url, args.db_user, args.db_password
    ).cache()

    event_schema = build_event_schema()

    raw = spark.readStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", args.kafka_bootstrap) \
        .option("subscribe", args.kafka_topics) \
        .option("startingOffsets", args.kafka_starting_offsets) \
        .load()

    parsed = raw.select(from_json(col("value").cast("string"), event_schema).alias("e")) \
        .select(
            col("e.eventType").alias("event_type"),
            col("e.data.userId").alias("user_id"),
            col("e.data.movieId").alias("movie_id"),
            col("e.data.rating").alias("rating"),
            col("e.data.operation").alias("operation"),
            col("e.data.contentLength").alias("content_length")
        )

    rating_value = coalesce(col("rating"), lit(0)).cast(DoubleType())
    # Normalize to 1-5 scale: if input > 5 assume legacy 10-50 and divide by 10
    rating_norm = when(rating_value > lit(5.0), rating_value / lit(10.0)).otherwise(rating_value)
    rating_safe = when(rating_norm <= lit(0.0), lit(1.0)).otherwise(rating_norm)

    base_weight = when(col("event_type") == lit("view_history"), lit(args.w_view)) \
        .when((col("event_type") == lit("favorite")) & (col("operation") == lit("ADD")), lit(args.w_favorite)) \
        .when((col("event_type") == lit("rating")) &
              (col("operation").isin("CREATE", "UPDATE")),
              rating_safe / lit(5.0) * lit(args.w_rating)) \
        .when((col("event_type") == lit("comment")) & (col("operation") == lit("CREATE")), lit(args.w_comment)) \
        .otherwise(lit(0.0))

    events = parsed \
        .withColumn("base_weight", base_weight) \
        .where(col("user_id").isNotNull()) \
        .where(col("movie_id").isNotNull()) \
        .where(col("base_weight") > lit(0.0))

    candidates = events.join(sim_df, events.movie_id == sim_df.movie_id, "inner") \
        .select(
            events.user_id.alias("user_id"),
            sim_df.similar_movie_id.alias("movie_id"),
            (events.base_weight * sim_df.similarity_score).alias("score")
        )
    # Exclude movies already watched or rated by the user
    candidates = candidates.join(history_df, ["user_id", "movie_id"], "left_anti")

    batch_scores = candidates.groupBy("user_id", "movie_id") \
        .agg(spark_sum(col("score")).alias("score"))

    def update_redis(batch_df, batch_id):
        if batch_df.rdd.isEmpty():
            return

        redis_host = args.redis_host
        redis_port = args.redis_port
        redis_db = args.redis_db
        redis_password = args.redis_password
        key_prefix = args.redis_key_prefix
        ttl_seconds = args.redis_ttl_hours * 3600 if args.redis_ttl_hours > 0 else 0
        top_n = args.top_n

        def process_partition(rows):
            import redis
            client = redis.Redis(
                host=redis_host,
                port=redis_port,
                db=redis_db,
                password=redis_password if redis_password else None,
                decode_responses=True
            )
            pipe = client.pipeline(transaction=False)
            touched_keys = set()
            for row in rows:
                user_id = row["user_id"]
                movie_id = row["movie_id"]
                score = row["score"]
                if user_id is None or movie_id is None or score is None:
                    continue
                key = f"{key_prefix}{user_id}"
                touched_keys.add(key)
                pipe.zincrby(key, float(score), str(movie_id))
            pipe.execute()

            for key in touched_keys:
                if ttl_seconds > 0:
                    client.expire(key, ttl_seconds)
                if top_n and top_n > 0:
                    size = client.zcard(key)
                    if size and size > top_n:
                        client.zremrangebyrank(key, 0, size - top_n - 1)

        batch_df.foreachPartition(process_partition)

    query = batch_scores.writeStream \
        .foreachBatch(update_redis) \
        .option("checkpointLocation", args.checkpoint) \
        .outputMode("update") \
        .start()

    query.awaitTermination()


if __name__ == "__main__":
    main()
