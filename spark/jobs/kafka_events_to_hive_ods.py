from __future__ import annotations

import argparse
from typing import Any

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import add_partitions
from utils.spark_factory import build_spark_session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Stream Kafka event logs into Hive ODS.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--checkpoint-root",
        default="",
        help="Optional override for streaming checkpoint root directory.",
    )
    parser.add_argument(
        "--trigger-processing-time",
        default="",
        help="Optional override for trigger interval, e.g. 30 seconds.",
    )
    parser.add_argument(
        "--run-mode",
        default="streaming",
        choices=["streaming", "available-now"],
        help="streaming keeps running; available-now consumes current backlog then exits.",
    )
    return parser.parse_args()


def normalize_events(source_df: DataFrame) -> DataFrame:
    value_col = F.col("value").cast("string")
    occurred_at_ms = F.get_json_object(value_col, "$.occurredAt").cast("long")
    occurred_at = F.to_timestamp(F.from_unixtime((occurred_at_ms / F.lit(1000)).cast("double")))
    event_ts = F.coalesce(occurred_at, F.col("timestamp"))

    return (
        source_df.select(
            F.col("topic").alias("topic"),
            F.col("key").cast("string").alias("event_key"),
            F.col("timestamp").alias("kafka_timestamp"),
            F.get_json_object(value_col, "$.eventId").alias("event_id"),
            F.get_json_object(value_col, "$.eventType").alias("event_type"),
            occurred_at.alias("occurred_at"),
            F.get_json_object(value_col, "$.data.userId").alias("user_id"),
            F.get_json_object(value_col, "$.data.movieId").cast("bigint").alias("movie_id"),
            F.get_json_object(value_col, "$.data.commentId").cast("bigint").alias("comment_id"),
            F.get_json_object(value_col, "$.data.folderId").cast("bigint").alias("folder_id"),
            F.get_json_object(value_col, "$.data.folderName").alias("folder_name"),
            F.get_json_object(value_col, "$.data.isPublic").cast("tinyint").alias("folder_is_public"),
            F.get_json_object(value_col, "$.data.operation").alias("operation"),
            F.get_json_object(value_col, "$.data.rating").cast("int").alias("rating"),
            F.get_json_object(value_col, "$.data.searchKeyword").alias("search_keyword"),
            F.get_json_object(value_col, "$.data.resultCount").cast("bigint").alias("result_count"),
            F.get_json_object(value_col, "$.data.filterConditions").alias("filter_conditions"),
            F.get_json_object(value_col, "$.data.searchTime").cast("bigint").alias("search_time"),
            F.get_json_object(value_col, "$.data").alias("event_data"),
            value_col.alias("raw_json"),
            F.current_timestamp().alias("ingest_time"),
            F.date_format(event_ts, "yyyy-MM-dd").alias("dt"),
            F.date_format(event_ts, "HH").alias("hh"),
        )
        .where(F.col("dt").isNotNull())
    )


def write_micro_batch(batch_df: DataFrame, batch_id: int, sink_path: str, sink_table: str) -> None:
    partition_rows = batch_df.select("dt", "hh").dropDuplicates().collect()
    if not partition_rows:
        return

    batch_df.write.mode("append").format("orc").partitionBy("dt", "hh").save(sink_path)
    add_partitions(
        table_name=sink_table,
        sink_path=sink_path,
        partition_specs=[{"dt": row["dt"], "hh": row["hh"]} for row in partition_rows],
        spark=batch_df.sparkSession,
    )
    print(
        "Kafka micro-batch committed. "
        f"batch_id={batch_id}, partition_cnt={len(partition_rows)}, sink_table={sink_table}"
    )


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    kafka_config: dict[str, Any] = config["kafka"]

    spark = build_spark_session(
        spark_config.get("app_name_streaming", "movie-kafka-to-hive-ods"),
        spark_config,
    )
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ods")

        topics = kafka_config["topics"]
        if not topics:
            raise ValueError("No Kafka topics configured.")

        reader = (
            spark.readStream.format("kafka")
            .option("kafka.bootstrap.servers", kafka_config["bootstrap_servers"])
            .option("subscribe", ",".join(topics))
            .option("startingOffsets", kafka_config.get("starting_offsets", "latest"))
            .option("failOnDataLoss", str(kafka_config.get("fail_on_data_loss", False)).lower())
        )

        max_offsets = kafka_config.get("max_offsets_per_trigger")
        if max_offsets:
            reader = reader.option("maxOffsetsPerTrigger", str(max_offsets))

        source_df = reader.load()
        sink_df = normalize_events(source_df)

        sink_table = kafka_config.get("sink_table", "").strip()
        if not sink_table:
            raise ValueError("sink_table is required in kafka config.")

        sink_path = kafka_config["sink_path"].rstrip("/")
        checkpoint_root = args.checkpoint_root or spark_config.get("checkpoint_root", "").rstrip("/")
        if not checkpoint_root:
            raise ValueError("checkpoint_root is required in args or config.")
        checkpoint_location = f"{checkpoint_root}/kafka_event_log"

        def _write_micro_batch(batch_df: DataFrame, batch_id: int) -> None:
            write_micro_batch(batch_df=batch_df, batch_id=batch_id, sink_path=sink_path, sink_table=sink_table)

        writer = sink_df.writeStream.outputMode("append").option(
            "checkpointLocation", checkpoint_location
        ).foreachBatch(_write_micro_batch)

        if args.run_mode == "available-now":
            writer = writer.trigger(availableNow=True)
        else:
            trigger = args.trigger_processing_time or spark_config.get("trigger_processing_time", "")
            if trigger:
                writer = writer.trigger(processingTime=trigger)

        query = writer.start()
        print(
            "Kafka -> Hive ODS stream started. "
            f"mode={args.run_mode}, topics={len(topics)}, sink_table={sink_table}, "
            f"sink_path={sink_path}, checkpoint={checkpoint_location}"
        )
        query.awaitTermination()
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
