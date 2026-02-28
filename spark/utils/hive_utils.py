from __future__ import annotations

import re
from datetime import date

from pyspark.sql import DataFrame, SparkSession

_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_TABLE_RE = re.compile(r"^[a-zA-Z_]\w*(\.[a-zA-Z_]\w*)*$")


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


def write_partition(
    df: DataFrame,
    table_name: str | None,
    sink_path: str,
    calc_date: str,
    spark: SparkSession,
) -> None:
    _validate_date(calc_date)
    if table_name is not None:
        _validate_table_name(table_name)

    target_partition_path = f"{sink_path.rstrip('/')}/dt={calc_date}"
    df.write.mode("overwrite").format("orc").save(target_partition_path)

    if table_name is None:
        return

    spark.sql(
        f"""
        ALTER TABLE {table_name}
        ADD IF NOT EXISTS PARTITION (dt='{calc_date}')
        LOCATION '{target_partition_path}'
        """
    )
