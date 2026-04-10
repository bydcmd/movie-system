from __future__ import annotations

import argparse
import json
import re
import urllib.request
import urllib.error
from datetime import datetime
from typing import Any

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.spark_factory import build_spark_session

_TABLE_NAME_PATTERN = re.compile(r"^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)?$")


def _validate_table_name(table_name: str) -> None:
    if not _TABLE_NAME_PATTERN.match(table_name):
        raise ValueError(f"Invalid table name: {table_name!r}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Poll Outbox table and write events to Hive ODS.")
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--batch-date",
        default="",
        help="Batch date in format YYYY-MM-DD. Defaults to today.",
    )
    return parser.parse_args()


def normalize_outbox_events(source_df: DataFrame) -> DataFrame:
    """
    Normalize outbox events into event log format.
    All session fields are stored together in the event table.
    """
    payload_col = F.col("payload")

    occurred_at_ms = F.get_json_object(payload_col, "$.occurredAt").cast("long")
    occurred_at = F.to_timestamp(F.from_unixtime((occurred_at_ms / F.lit(1000)).cast("double")))
    event_ts = F.coalesce(occurred_at, F.col("created_at"))

    session_ctx = F.get_json_object(payload_col, "$.sessionContext")

    return (
        source_df.select(
            F.col("id").alias("outbox_id"),
            F.col("topic").alias("topic"),
            F.col("message_key").cast("string").alias("event_key"),
            F.col("created_at").alias("kafka_timestamp"),
            F.get_json_object(payload_col, "$.eventId").alias("event_id"),
            F.get_json_object(payload_col, "$.eventType").alias("event_type"),
            occurred_at.alias("occurred_at"),
            F.get_json_object(payload_col, "$.data.userId").alias("user_id"),
            F.get_json_object(payload_col, "$.data.movieId").cast("bigint").alias("movie_id"),
            F.get_json_object(payload_col, "$.data.commentId").cast("bigint").alias("comment_id"),
            F.get_json_object(payload_col, "$.data.folderId").cast("bigint").alias("folder_id"),
            F.get_json_object(payload_col, "$.data.folderName").alias("folder_name"),
            F.get_json_object(payload_col, "$.data.isPublic").cast("tinyint").alias("folder_is_public"),
            F.get_json_object(payload_col, "$.data.operation").alias("operation"),
            F.get_json_object(payload_col, "$.data.rating").cast("int").alias("rating"),
            F.get_json_object(payload_col, "$.data.searchKeyword").alias("search_keyword"),
            F.get_json_object(payload_col, "$.data.resultCount").cast("bigint").alias("result_count"),
            F.get_json_object(payload_col, "$.data.filterConditions").alias("filter_conditions"),
            F.get_json_object(payload_col, "$.data.searchTime").cast("bigint").alias("search_time"),
            F.get_json_object(payload_col, "$.data").alias("event_data"),
            payload_col.alias("raw_json"),
            F.current_timestamp().alias("ingest_time"),
            F.date_format(event_ts, "yyyy-MM-dd").alias("dt"),
            F.date_format(event_ts, "HH").alias("hh"),
            # Session fields
            F.get_json_object(session_ctx, "$.sessionId").alias("session_id"),
            F.get_json_object(session_ctx, "$.pageUrl").alias("page_url"),
            F.get_json_object(session_ctx, "$.sequenceNumber").cast("int").alias("sequence_number"),
            F.get_json_object(session_ctx, "$.clientTimestamp").cast("bigint").alias("client_timestamp"),
            F.get_json_object(session_ctx, "$.entryUrl").alias("entry_url"),
            F.get_json_object(session_ctx, "$.referrer").alias("first_referrer"),
            F.get_json_object(session_ctx, "$.sessionStartTime").cast("bigint").alias("session_start_time"),
            F.get_json_object(session_ctx, "$.deviceType").alias("device_type"),
            F.get_json_object(session_ctx, "$.userAgent").alias("user_agent"),
        )
        .where(F.col("dt").isNotNull())
    )


def write_batch(
    batch_df: DataFrame,
    sink_path: str,
    sink_table: str,
) -> None:
    """
    Write a batch to Hive ODS event log table.
    Uses MSCK REPAIR TABLE for partition discovery.
    """
    _validate_table_name(sink_table)

    batch_df.write.mode("append").format("orc").partitionBy("dt", "hh").save(sink_path)
    batch_df.sparkSession.sql(f"MSCK REPAIR TABLE {sink_table}")

    print(f"Outbox batch written. sink_table={sink_table}, events={batch_df.count()}")


def call_backend_mark_sent(backend_url: str, ids: list[int]) -> int:
    """
    Call backend /internal/outbox/mark-sent endpoint to mark events as sent.
    Returns the number of events marked.
    """
    if not ids:
        return 0
    url = f"{backend_url}/internal/outbox/mark-sent"
    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(ids).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.loads(resp.read().decode("utf-8"))
            marked = result.get("marked", 0)
            print(f"Backend mark-sent response: marked={marked}")
            return marked
    except urllib.error.URLError as e:
        print(f"Failed to call backend mark-sent: {e}")
        raise


def run() -> None:
    """
    Poll Outbox table from PostgreSQL and write events to Hive ODS.

    Outbox table (event_outbox) schema:
    - id: Primary key
    - topic: Event topic name
    - message_key: Event key (user_id)
    - payload: JSON event data
    - status: 0=pending, 1=sent, 2=processing, 3=failed
    - created_at: Creation timestamp

    After writing to ODS, calls backend /internal/outbox/mark-sent to mark events as sent.
    """
    args = parse_args()
    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    pg_config: dict[str, Any] = config["postgres"]

    batch_date = args.batch_date or datetime.today().strftime("%Y-%m-%d")

    spark = build_spark_session(
        spark_config.get("app_name_batch", "movie-outbox-to-hive-ods"),
        spark_config,
    )
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ods")

        outbox_config = config.get("outbox", {})
        sink_table = outbox_config.get("sink_table", "").strip()
        if not sink_table:
            raise ValueError("outbox.sink_table is required in config.")
        sink_path = outbox_config.get("sink_path", "").strip()
        if not sink_path:
            raise ValueError("outbox.sink_path is required in config.")
        backend_url = outbox_config.get("backend_url", "").strip()
        if not backend_url:
            raise ValueError("outbox.backend_url is required in config.")

        jdbc_url = pg_config["jdbc_url"]
        jdbc_table = outbox_config.get("source_table", "event_outbox").strip()

        print(
            f"Reading outbox table. jdbc_url={jdbc_url}, table={jdbc_table}, batch_date={batch_date}"
        )

        outbox_df = (
            spark.read.format("jdbc")
            .option("url", jdbc_url)
            .option("dbtable", jdbc_table)
            .option("driver", pg_config.get("driver", "org.postgresql.Driver"))
            .option("user", pg_config["user"])
            .option("password", pg_config["password"])
            .option("partitionColumn", "id")
            .option("lowerBound", "1")
            .option("upperBound", "999999999")
            .option("numPartitions", str(spark_config.get("spark.sql.shuffle.partitions", 4)))
            .load()
        )

        pending_df = outbox_df.filter(
            (F.col("status") == 0) & (F.col("created_at") >= F.lit(batch_date))
        )

        normalized_df = normalize_outbox_events(pending_df)

        # Collect outbox IDs for marking (limited to avoid driver OOM)
        outbox_ids = [
            row.outbox_id
            for row in normalized_df.select("outbox_id").limit(10000).collect()
        ]

        # Drop outbox_id before writing to ODS (not part of ODS schema)
        ods_df = normalized_df.drop("outbox_id")

        write_batch(
            batch_df=ods_df,
            sink_path=sink_path,
            sink_table=sink_table,
        )

        # Mark events as sent via backend
        if outbox_ids:
            marked = call_backend_mark_sent(backend_url, outbox_ids)
            print(f"Marked {marked} events as sent. batch_date={batch_date}")
        else:
            print(f"No pending events found for batch_date={batch_date}")

        print(
            f"Outbox to ODS batch finished. batch_date={batch_date}, "
            f"sink_table={sink_table}, events_processed={len(outbox_ids)}"
        )
    finally:
        try:
            spark.catalog.clearCache()
        except Exception:
            pass
        spark.stop()


if __name__ == "__main__":
    run()
