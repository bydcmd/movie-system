from __future__ import annotations

from typing import Any

from pyspark.sql import SparkSession


def build_spark_session(app_name: str, spark_config: dict[str, Any]) -> SparkSession:
    builder = SparkSession.builder.appName(app_name)

    master = spark_config.get("master")
    if master:
        builder = builder.master(master)

    warehouse_dir = spark_config.get("warehouse_dir")
    if warehouse_dir:
        builder = builder.config("spark.sql.warehouse.dir", warehouse_dir)

    shuffle_partitions = spark_config.get("shuffle_partitions")
    if shuffle_partitions:
        builder = builder.config("spark.sql.shuffle.partitions", str(shuffle_partitions))

    builder = (
        builder.config("spark.sql.adaptive.enabled", "true")
        .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
        .config("spark.sql.sources.partitionOverwriteMode", "dynamic")
    )

    return builder.enableHiveSupport().getOrCreate()
