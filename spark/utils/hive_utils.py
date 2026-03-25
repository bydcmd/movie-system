from __future__ import annotations

import re
from datetime import date

from pyspark.sql import DataFrame, SparkSession

_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_HOUR_RE = re.compile(r"^\d{2}$")
_TABLE_RE = re.compile(r"^[a-zA-Z_]\w*(\.[a-zA-Z_]\w*)*$")
_PARTITION_COLUMN_RE = re.compile(r"^[a-zA-Z_]\w*$")


def _validate_date(value: str) -> None:
    if not _DATE_RE.match(value):
        raise ValueError(f"Invalid date format: {value!r}, expected YYYY-MM-DD")
    date.fromisoformat(value)


def _validate_table_name(value: str) -> None:
    if not _TABLE_RE.match(value):
        raise ValueError(
            f"Invalid table name: {value!r}, "
            "only alphanumerics, underscores and dots allowed"
        )


def _validate_hour(value: str) -> None:
    if not _HOUR_RE.match(value):
        raise ValueError(f"Invalid hour format: {value!r}, expected HH")
    hour = int(value)
    if hour < 0 or hour > 23:
        raise ValueError(f"Invalid hour value: {value!r}, expected 00-23")


def _validate_partition_column(value: str) -> None:
    if not _PARTITION_COLUMN_RE.match(value):
        raise ValueError(f"Invalid partition column name: {value!r}")


def _validate_partition_value(column_name: str, value: str) -> None:
    if column_name == "dt":
        _validate_date(value)
        return
    if column_name == "hh":
        _validate_hour(value)
        return
    if value == "":
        raise ValueError(f"Partition value for column {column_name!r} must not be empty")


def _quote_sql_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "\\'")


def add_partition(
    table_name: str,
    sink_path: str,
    partition_spec: dict[str, str],
    spark: SparkSession,
) -> None:
    _validate_table_name(table_name)

    if not partition_spec:
        raise ValueError("partition_spec must not be empty")

    target_partition_path = sink_path.rstrip("/")
    partition_items: list[str] = []
    for column_name, partition_value in partition_spec.items():
        _validate_partition_column(column_name)
        _validate_partition_value(column_name, partition_value)
        target_partition_path = f"{target_partition_path}/{column_name}={partition_value}"
        partition_items.append(f"{column_name}='{_quote_sql_string(partition_value)}'")

    spark.sql(
        f"""
        ALTER TABLE {table_name}
        ADD IF NOT EXISTS PARTITION ({", ".join(partition_items)})
        LOCATION '{_quote_sql_string(target_partition_path)}'
        """
    )


def add_partitions(
    table_name: str,
    sink_path: str,
    partition_specs: list[dict[str, str]],
    spark: SparkSession,
) -> None:
    for partition_spec in partition_specs:
        add_partition(
            table_name=table_name,
            sink_path=sink_path,
            partition_spec=partition_spec,
            spark=spark,
        )


def write_partition(
    df: DataFrame,
    table_name: str | None,
    sink_path: str,
    calc_date: str,
    spark: SparkSession,
) -> None:
    _validate_date(calc_date)

    target_partition_path = f"{sink_path.rstrip('/')}/dt={calc_date}"
    df.write.mode("overwrite").format("orc").save(target_partition_path)

    if table_name is None:
        return

    add_partition(
        table_name=table_name,
        sink_path=sink_path,
        partition_spec={"dt": calc_date},
        spark=spark,
    )
