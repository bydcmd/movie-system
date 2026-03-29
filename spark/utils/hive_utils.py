from __future__ import annotations

import re
from datetime import date

from pyspark.sql import DataFrame, SparkSession

_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_HOUR_RE = re.compile(r"^\d{2}$")
_TABLE_RE = re.compile(r"^[a-zA-Z_]\w*(\.[a-zA-Z_]\w*)*$")
_PARTITION_COLUMN_RE = re.compile(r"^[a-zA-Z_]\w*$")
_PARTITION_ITEM_RE = re.compile(r"^([a-zA-Z_]\w*)=(.*)$")


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


def _parse_partition_spec(spec: str) -> dict[str, str]:
    partition_spec: dict[str, str] = {}
    for item in spec.split("/"):
        match = _PARTITION_ITEM_RE.match(item)
        if match is None:
            raise ValueError(f"Invalid partition spec item: {item!r}")

        column_name, partition_value = match.groups()
        _validate_partition_column(column_name)
        _validate_partition_value(column_name, partition_value)
        partition_spec[column_name] = partition_value
    return partition_spec


def list_partition_values(
    table_name: str,
    partition_column: str,
    spark: SparkSession,
) -> list[str]:
    _validate_table_name(table_name)
    _validate_partition_column(partition_column)

    partition_values: set[str] = set()
    rows = spark.sql(f"SHOW PARTITIONS {table_name}").collect()
    for row in rows:
        partition_spec = _parse_partition_spec(row[0])
        partition_value = partition_spec.get(partition_column)
        if partition_value is not None:
            partition_values.add(partition_value)

    return sorted(partition_values)


def _format_partition_spec(partition_spec: dict[str, str]) -> str:
    partition_items: list[str] = []
    for column_name, partition_value in partition_spec.items():
        _validate_partition_column(column_name)
        _validate_partition_value(column_name, partition_value)
        partition_items.append(f"{column_name}={partition_value}")
    return ", ".join(partition_items)


def partition_exists(
    table_name: str,
    partition_spec: dict[str, str],
    spark: SparkSession,
) -> bool:
    _validate_table_name(table_name)

    if not partition_spec:
        raise ValueError("partition_spec must not be empty")

    normalized_partition_spec = {
        column_name: partition_value for column_name, partition_value in partition_spec.items()
    }
    _format_partition_spec(normalized_partition_spec)

    rows = spark.sql(f"SHOW PARTITIONS {table_name}").collect()
    for row in rows:
        parsed_partition_spec = _parse_partition_spec(row[0])
        if all(parsed_partition_spec.get(column_name) == partition_value for column_name, partition_value in normalized_partition_spec.items()):
            return True

    return False


def resolve_dt_partition_date(
    table_name: str,
    requested_date: str,
    spark: SparkSession,
    fallback_max_date: str | None = None,
) -> str:
    if requested_date:
        _validate_date(requested_date)
        return requested_date

    available_dates = list_partition_values(table_name, "dt", spark)
    if fallback_max_date is not None:
        _validate_date(fallback_max_date)
        available_dates = [value for value in available_dates if value <= fallback_max_date]

    if not available_dates:
        if fallback_max_date is not None:
            raise ValueError(f"No dt partitions found in {table_name} on or before {fallback_max_date}")
        raise ValueError(f"No dt partitions found in {table_name}")

    return available_dates[-1]


def resolve_common_dt_partition_date(
    table_names: list[str],
    requested_date: str,
    spark: SparkSession,
    fallback_max_date: str | None = None,
) -> str:
    if requested_date:
        _validate_date(requested_date)
        return requested_date

    if not table_names:
        raise ValueError("table_names must not be empty")

    common_dates: set[str] | None = None
    for table_name in table_names:
        available_dates = set(list_partition_values(table_name, "dt", spark))
        if fallback_max_date is not None:
            _validate_date(fallback_max_date)
            available_dates = {value for value in available_dates if value <= fallback_max_date}

        if not available_dates:
            if fallback_max_date is not None:
                raise ValueError(f"No dt partitions found in {table_name} on or before {fallback_max_date}")
            raise ValueError(f"No dt partitions found in {table_name}")

        common_dates = available_dates if common_dates is None else common_dates & available_dates
        if not common_dates:
            joined_tables = ", ".join(table_names)
            if fallback_max_date is not None:
                raise ValueError(
                    f"No common dt partition found across tables on or before {fallback_max_date}: {joined_tables}"
                )
            raise ValueError(f"No common dt partition found across tables: {joined_tables}")

    return max(common_dates)


def assert_non_empty_partition(
    df: DataFrame,
    table_name: str,
    partition_spec: dict[str, str],
    spark: SparkSession | None = None,
) -> None:
    if df.limit(1).count() > 0:
        return

    if not partition_spec:
        raise ValueError(f"No data found in {table_name}")

    formatted_partition_spec = _format_partition_spec(partition_spec)
    if spark is not None:
        if partition_exists(table_name, partition_spec, spark):
            raise ValueError(
                f"Partition {formatted_partition_spec} exists in {table_name}, "
                "but Spark returned 0 rows. This usually means the partition directory is empty, "
                "the files under the partition are unreadable/incompatible, or the partition was "
                "registered before data was actually written."
            )
        raise ValueError(
            f"No data found in {table_name} for partition {formatted_partition_spec}. "
            "Spark metastore did not list this partition. If Hive CLI can see it, check whether "
            "Spark and Hive are using different metastore or warehouse settings."
        )

    raise ValueError(f"No data found in {table_name} for partition {formatted_partition_spec}")


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
