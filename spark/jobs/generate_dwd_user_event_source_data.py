from __future__ import annotations

import argparse
import copy
import datetime as dt
import math
import random
from decimal import Decimal
from typing import Any

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import types as T

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.hive_utils import write_partition
from utils.spark_factory import build_spark_session


DEFAULT_GENERATOR_CONFIG: dict[str, Any] = {
    "movie_limit": 500,
    "user_target": 300,
    "new_user_target": 30,
    "folder_target": 450,
    "view_target": 8000,
    "rating_target": 2500,
    "comment_target": 1500,
    "comment_like_target": 2500,
    "favorite_target": 2000,
    "watched_target": 1800,
    "sample_ratio": 0.35,
    "lookback_days": 90,
    "seed": 20260412,
    "jdbc_query_limit_cap": 20000,
}

REQUIRED_SOURCE_TABLES: tuple[str, ...] = (
    "public.movies",
    "public.users",
    "public.favorite_folders",
    "public.favorites",
    "public.ratings",
    "public.comments",
    "public.comment_likes",
    "public.view_history",
    "public.watched_movies",
)

MOVIES_SCHEMA = T.StructType(
    [
        T.StructField("movie_id", T.LongType(), False),
        T.StructField("name", T.StringType(), False),
        T.StructField("alias", T.StringType(), True),
        T.StructField("actors", T.StringType(), True),
        T.StructField("cover", T.StringType(), True),
        T.StructField("directors", T.StringType(), True),
        T.StructField("douban_score", T.DecimalType(3, 1), True),
        T.StructField("score", T.DecimalType(3, 1), True),
        T.StructField("douban_votes", T.IntegerType(), True),
        T.StructField("votes", T.IntegerType(), True),
        T.StructField("genres", T.StringType(), True),
        T.StructField("imdb_id", T.StringType(), True),
        T.StructField("languages", T.StringType(), True),
        T.StructField("mins", T.StringType(), True),
        T.StructField("regions", T.StringType(), True),
        T.StructField("release_date", T.StringType(), True),
        T.StructField("storyline", T.StringType(), True),
        T.StructField("year", T.IntegerType(), True),
        T.StructField("writers", T.StringType(), True),
        T.StructField("rating_weights", T.StringType(), True),
        T.StructField("full_search_text", T.StringType(), True),
    ]
)

USERS_SCHEMA = T.StructType(
    [
        T.StructField("user_id", T.StringType(), False),
        T.StructField("user_nickname", T.StringType(), True),
        T.StructField("user_password", T.StringType(), True),
        T.StructField("user_avatar", T.StringType(), True),
        T.StructField("user_url", T.StringType(), True),
        T.StructField("role", T.ShortType(), True),
        T.StructField("status", T.IntegerType(), True),
        T.StructField("password_version", T.IntegerType(), True),
        T.StructField("email", T.StringType(), True),
        T.StructField("create_time", T.TimestampType(), True),
        T.StructField("update_time", T.TimestampType(), True),
    ]
)

FOLDERS_SCHEMA = T.StructType(
    [
        T.StructField("id", T.LongType(), False),
        T.StructField("user_id", T.StringType(), False),
        T.StructField("name", T.StringType(), False),
        T.StructField("description", T.StringType(), True),
        T.StructField("is_public", T.ShortType(), True),
        T.StructField("movie_count", T.IntegerType(), True),
        T.StructField("create_time", T.TimestampType(), True),
        T.StructField("update_time", T.TimestampType(), True),
    ]
)

FAVORITES_SCHEMA = T.StructType(
    [
        T.StructField("user_id", T.StringType(), False),
        T.StructField("movie_id", T.LongType(), False),
        T.StructField("folder_id", T.LongType(), False),
        T.StructField("create_time", T.TimestampType(), True),
    ]
)

RATINGS_SCHEMA = T.StructType(
    [
        T.StructField("user_id", T.StringType(), False),
        T.StructField("movie_id", T.LongType(), False),
        T.StructField("rating", T.IntegerType(), True),
        T.StructField("rating_time", T.TimestampType(), True),
    ]
)

COMMENTS_SCHEMA = T.StructType(
    [
        T.StructField("comment_id", T.LongType(), False),
        T.StructField("user_id", T.StringType(), True),
        T.StructField("movie_id", T.LongType(), True),
        T.StructField("content", T.StringType(), True),
        T.StructField("votes", T.IntegerType(), True),
        T.StructField("comment_time", T.TimestampType(), True),
        T.StructField("title", T.StringType(), True),
        T.StructField("type", T.ShortType(), True),
        T.StructField("version", T.IntegerType(), True),
    ]
)

COMMENT_LIKES_SCHEMA = T.StructType(
    [
        T.StructField("id", T.LongType(), False),
        T.StructField("comment_id", T.LongType(), False),
        T.StructField("user_id", T.StringType(), False),
        T.StructField("create_time", T.TimestampType(), True),
    ]
)

VIEW_HISTORY_SCHEMA = T.StructType(
    [
        T.StructField("history_id", T.LongType(), False),
        T.StructField("user_id", T.StringType(), False),
        T.StructField("movie_id", T.LongType(), False),
        T.StructField("view_time", T.TimestampType(), True),
    ]
)

WATCHED_SCHEMA = T.StructType(
    [
        T.StructField("user_id", T.StringType(), False),
        T.StructField("movie_id", T.LongType(), False),
        T.StructField("create_time", T.TimestampType(), True),
    ]
)

DEFAULT_PASSWORD_HASH = "$2a$10$lPHc.uX1uT4Q/54HYO9DfO8B4TCOJYAZGsaemn0pLxA3OoHQeOd5S"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate PostgreSQL-aligned ODS snapshot test data for the DWD/DWS pipeline."
    )
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--batch-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="ODS snapshot partition date in format YYYY-MM-DD.",
    )
    parser.add_argument("--movie-limit", type=int, default=None, help="Sampled real movie rows to keep.")
    parser.add_argument("--user-target", type=int, default=None, help="Target row count for ods_pg_users_full.")
    parser.add_argument(
        "--new-user-target",
        type=int,
        default=None,
        help="Target count for users registered on the batch-date.",
    )
    parser.add_argument("--folder-target", type=int, default=None, help="Target row count for ods_pg_favorite_folders_full.")
    parser.add_argument("--view-target", type=int, default=None, help="Target row count for ods_pg_view_history_full.")
    parser.add_argument("--rating-target", type=int, default=None, help="Target row count for ods_pg_ratings_full.")
    parser.add_argument("--comment-target", type=int, default=None, help="Target row count for ods_pg_comments_full.")
    parser.add_argument(
        "--comment-like-target",
        type=int,
        default=None,
        help="Target row count for ods_pg_comment_likes_full.",
    )
    parser.add_argument("--favorite-target", type=int, default=None, help="Target row count for ods_pg_favorites_full.")
    parser.add_argument("--watched-target", type=int, default=None, help="Target row count for ods_pg_watched_movies_full.")
    parser.add_argument("--sample-ratio", type=float, default=None, help="Share of non-movie rows sampled from PostgreSQL.")
    parser.add_argument("--lookback-days", type=int, default=None, help="How many historical days synthetic timestamps may span.")
    parser.add_argument("--seed", type=int, default=None, help="Random seed.")
    return parser.parse_args()


def merge_nested_dict(defaults: dict[str, Any], overrides: dict[str, Any]) -> dict[str, Any]:
    merged = copy.deepcopy(defaults)
    for key, value in overrides.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = merge_nested_dict(merged[key], value)
        else:
            merged[key] = value
    return merged


def apply_arg_overrides(generator_config: dict[str, Any], args: argparse.Namespace) -> dict[str, Any]:
    key_mapping = {
        "movie_limit": args.movie_limit,
        "user_target": args.user_target,
        "new_user_target": args.new_user_target,
        "folder_target": args.folder_target,
        "view_target": args.view_target,
        "rating_target": args.rating_target,
        "comment_target": args.comment_target,
        "comment_like_target": args.comment_like_target,
        "favorite_target": args.favorite_target,
        "watched_target": args.watched_target,
        "sample_ratio": args.sample_ratio,
        "lookback_days": args.lookback_days,
        "seed": args.seed,
    }
    merged = dict(generator_config)
    for key, value in key_mapping.items():
        if value is not None:
            merged[key] = value
    return merged


def quote_sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def in_clause_string(values: list[str]) -> str:
    if not values:
        return "(NULL)"
    return "(" + ", ".join(quote_sql_string(item) for item in values) + ")"


def in_clause_bigint(values: list[int]) -> str:
    if not values:
        return "(NULL)"
    return "(" + ", ".join(str(int(item)) for item in values) + ")"


def build_jdbc_df(spark: SparkSession, pg_config: dict[str, Any], query: str) -> DataFrame:
    return (
        spark.read.format("jdbc")
        .option("url", pg_config["jdbc_url"])
        .option("dbtable", f"({query}) src")
        .option("driver", pg_config.get("driver", "org.postgresql.Driver"))
        .option("user", pg_config["user"])
        .option("password", pg_config["password"])
        .option("fetchsize", str(pg_config.get("fetch_size", 10000)))
        .load()
    )


def fetch_records(spark: SparkSession, pg_config: dict[str, Any], query: str) -> list[dict[str, Any]]:
    return [row.asDict(recursive=True) for row in build_jdbc_df(spark, pg_config, query).collect()]


def build_postgres_target_map(config: dict[str, Any]) -> dict[str, dict[str, str]]:
    table_map: dict[str, dict[str, str]] = {}
    for table_cfg in config["postgres"]["tables"]:
        source_table = table_cfg.get("source_table")
        if not source_table:
            continue
        table_map[source_table] = {
            "target_table": table_cfg["target_table"],
            "sink_path": table_cfg["sink_path"],
        }

    missing_tables = [table_name for table_name in REQUIRED_SOURCE_TABLES if table_name not in table_map]
    if missing_tables:
        raise ValueError(f"Missing postgres.tables config for: {', '.join(missing_tables)}")

    return table_map


def create_dataframe(spark: SparkSession, schema: T.StructType, records: list[dict[str, Any]]) -> DataFrame:
    if not records:
        return spark.createDataFrame([], schema)

    normalized_records: list[dict[str, Any]] = []
    field_names = [field.name for field in schema.fields]
    for record in records:
        normalized_records.append({field_name: record.get(field_name) for field_name in field_names})
    return spark.createDataFrame(normalized_records, schema=schema)


def ceil_sample_count(target: int, sample_ratio: float, hard_cap: int) -> int:
    if target <= 0 or sample_ratio <= 0:
        return 0
    return min(hard_cap, max(0, math.ceil(target * sample_ratio)))


def parse_batch_date(batch_date: str) -> dt.date:
    return dt.date.fromisoformat(batch_date)


def recent_timestamp(
    rng: random.Random,
    batch_date: dt.date,
    lookback_days: int,
    not_before: dt.datetime | None = None,
) -> dt.datetime:
    capped_lookback = max(1, lookback_days)
    max_days_ago = capped_lookback - 1
    days_ago = int(rng.triangular(0, max_days_ago, 0))
    event_day = batch_date - dt.timedelta(days=days_ago)
    event_time = dt.datetime.combine(
        event_day,
        dt.time(hour=rng.randint(0, 23), minute=rng.randint(0, 59), second=rng.randint(0, 59)),
    )
    if not_before is None:
        return event_time
    if event_time >= not_before:
        return event_time
    end_time = dt.datetime.combine(batch_date, dt.time(23, 59, 59))
    if not_before > end_time:
        return end_time
    delta_seconds = int((end_time - not_before).total_seconds())
    return not_before + dt.timedelta(seconds=rng.randint(0, max(delta_seconds, 0)))


def batch_date_timestamp(
    rng: random.Random,
    batch_date: dt.date,
    not_before: dt.datetime | None = None,
) -> dt.datetime:
    start_time = dt.datetime.combine(batch_date, dt.time(0, 0, 0))
    end_time = dt.datetime.combine(batch_date, dt.time(23, 59, 59))
    effective_start = start_time if not_before is None or not_before < start_time else not_before
    if effective_start >= end_time:
        return end_time
    delta_seconds = int((end_time - effective_start).total_seconds())
    return effective_start + dt.timedelta(seconds=rng.randint(0, max(delta_seconds, 0)))


def is_registered_on_batch_date(record: dict[str, Any], batch_date: dt.date) -> bool:
    create_time = record.get("create_time")
    if create_time is None:
        return False
    if isinstance(create_time, dt.datetime):
        return create_time.date() == batch_date
    if isinstance(create_time, dt.date):
        return create_time == batch_date
    return False


def build_synthetic_user(
    user_id: str,
    nickname_index: int,
    create_time: dt.datetime,
    update_time: dt.datetime,
) -> dict[str, Any]:
    return {
        "user_id": user_id,
        "user_nickname": f"测试用户{nickname_index}",
        "user_password": DEFAULT_PASSWORD_HASH,
        "user_avatar": f"https://example.com/avatar/{user_id}.png",
        "user_url": f"https://example.com/users/{user_id}",
        "role": 1,
        "status": 0,
        "password_version": 1,
        "email": f"{user_id}@example.test",
        "create_time": create_time,
        "update_time": update_time,
    }


def safe_decimal(value: Any) -> Decimal | None:
    if value is None:
        return None
    if isinstance(value, Decimal):
        return value
    return Decimal(str(value))


def sample_movies(spark: SparkSession, pg_config: dict[str, Any], movie_limit: int) -> list[dict[str, Any]]:
    query = f"""
        SELECT
          movie_id,
          name,
          alias,
          CAST(actors AS text) AS actors,
          cover,
          CAST(directors AS text) AS directors,
          CAST(douban_score AS numeric(3,1)) AS douban_score,
          CAST(score AS numeric(3,1)) AS score,
          douban_votes,
          votes,
          genres,
          imdb_id,
          languages,
          mins,
          regions,
          release_date,
          storyline,
          year,
          CAST(writers AS text) AS writers,
          CAST(rating_weights AS text) AS rating_weights,
          full_search_text
        FROM public.movies
        WHERE movie_id IS NOT NULL
          AND name IS NOT NULL
        ORDER BY RANDOM()
        LIMIT {int(movie_limit)}
    """
    records = fetch_records(spark, pg_config, query)
    if not records:
        raise ValueError("No rows sampled from public.movies. Movie rows must come from the business database.")
    for record in records:
        record["douban_score"] = safe_decimal(record.get("douban_score"))
        record["score"] = safe_decimal(record.get("score"))
    return records


def sample_users(spark: SparkSession, pg_config: dict[str, Any], user_limit: int) -> list[dict[str, Any]]:
    if user_limit <= 0:
        return []
    query = f"""
        SELECT
          user_id,
          user_nickname,
          user_password,
          user_avatar,
          user_url,
          COALESCE(role, 1) AS role,
          COALESCE(status, 0) AS status,
          COALESCE(password_version, 1) AS password_version,
          email,
          create_time,
          update_time
        FROM public.users
        WHERE user_id IS NOT NULL
        ORDER BY RANDOM()
        LIMIT {int(user_limit)}
    """
    return fetch_records(spark, pg_config, query)


def sample_folders(spark: SparkSession, pg_config: dict[str, Any], user_ids: list[str], folder_limit: int) -> list[dict[str, Any]]:
    if folder_limit <= 0 or not user_ids:
        return []
    query = f"""
        SELECT
          id,
          user_id,
          name,
          description,
          COALESCE(is_public, 0) AS is_public,
          COALESCE(movie_count, 0) AS movie_count,
          create_time,
          update_time
        FROM public.favorite_folders
        WHERE user_id IN {in_clause_string(user_ids)}
        ORDER BY RANDOM()
        LIMIT {int(folder_limit)}
    """
    return fetch_records(spark, pg_config, query)


def sample_favorites(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_ids: list[str],
    movie_ids: list[int],
    folder_ids: list[int],
    favorite_limit: int,
) -> list[dict[str, Any]]:
    if favorite_limit <= 0 or not user_ids or not movie_ids or not folder_ids:
        return []
    query = f"""
        SELECT
          user_id,
          movie_id,
          folder_id,
          create_time
        FROM public.favorites
        WHERE user_id IN {in_clause_string(user_ids)}
          AND movie_id IN {in_clause_bigint(movie_ids)}
          AND folder_id IN {in_clause_bigint(folder_ids)}
        ORDER BY RANDOM()
        LIMIT {int(favorite_limit)}
    """
    return fetch_records(spark, pg_config, query)


def sample_ratings(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_ids: list[str],
    movie_ids: list[int],
    rating_limit: int,
) -> list[dict[str, Any]]:
    if rating_limit <= 0 or not user_ids or not movie_ids:
        return []
    query = f"""
        SELECT
          user_id,
          movie_id,
          rating,
          rating_time
        FROM public.ratings
        WHERE user_id IN {in_clause_string(user_ids)}
          AND movie_id IN {in_clause_bigint(movie_ids)}
          AND rating_time IS NOT NULL
        ORDER BY RANDOM()
        LIMIT {int(rating_limit)}
    """
    return fetch_records(spark, pg_config, query)


def sample_comments(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_ids: list[str],
    movie_ids: list[int],
    comment_limit: int,
) -> list[dict[str, Any]]:
    if comment_limit <= 0 or not user_ids or not movie_ids:
        return []
    query = f"""
        SELECT
          comment_id,
          user_id,
          movie_id,
          content,
          COALESCE(votes, 0) AS votes,
          comment_time,
          title,
          COALESCE(type, 1) AS type,
          COALESCE(version, 0) AS version
        FROM public.comments
        WHERE user_id IN {in_clause_string(user_ids)}
          AND movie_id IN {in_clause_bigint(movie_ids)}
          AND comment_time IS NOT NULL
        ORDER BY RANDOM()
        LIMIT {int(comment_limit)}
    """
    return fetch_records(spark, pg_config, query)


def sample_comment_likes(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_ids: list[str],
    comment_ids: list[int],
    comment_like_limit: int,
) -> list[dict[str, Any]]:
    if comment_like_limit <= 0 or not user_ids or not comment_ids:
        return []
    query = f"""
        SELECT
          id,
          comment_id,
          user_id,
          create_time
        FROM public.comment_likes
        WHERE user_id IN {in_clause_string(user_ids)}
          AND comment_id IN {in_clause_bigint(comment_ids)}
          AND create_time IS NOT NULL
        ORDER BY RANDOM()
        LIMIT {int(comment_like_limit)}
    """
    return fetch_records(spark, pg_config, query)


def sample_view_history(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_ids: list[str],
    movie_ids: list[int],
    history_limit: int,
) -> list[dict[str, Any]]:
    if history_limit <= 0 or not user_ids or not movie_ids:
        return []
    query = f"""
        SELECT
          history_id,
          user_id,
          movie_id,
          view_time
        FROM public.view_history
        WHERE user_id IN {in_clause_string(user_ids)}
          AND movie_id IN {in_clause_bigint(movie_ids)}
          AND view_time IS NOT NULL
        ORDER BY RANDOM()
        LIMIT {int(history_limit)}
    """
    return fetch_records(spark, pg_config, query)


def sample_watched_movies(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_ids: list[str],
    movie_ids: list[int],
    watched_limit: int,
) -> list[dict[str, Any]]:
    if watched_limit <= 0 or not user_ids or not movie_ids:
        return []
    query = f"""
        SELECT
          user_id,
          movie_id,
          create_time
        FROM public.watched_movies
        WHERE user_id IN {in_clause_string(user_ids)}
          AND movie_id IN {in_clause_bigint(movie_ids)}
          AND create_time IS NOT NULL
        ORDER BY RANDOM()
        LIMIT {int(watched_limit)}
    """
    return fetch_records(spark, pg_config, query)


def generate_users(
    existing_users: list[dict[str, Any]],
    user_target: int,
    new_user_target: int,
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    required_new_users = min(max(new_user_target, 0), max(user_target, 0))
    sampled_new_users = [record for record in existing_users if is_registered_on_batch_date(record, batch_date)]
    sampled_other_users = [record for record in existing_users if not is_registered_on_batch_date(record, batch_date)]

    selected_new_users = sampled_new_users[:required_new_users]
    records = list(selected_new_users)
    selected_ids = {record["user_id"] for record in records}
    remaining_slots = max(0, user_target - required_new_users)
    selected_other_count = 0
    for record in sampled_other_users:
        if selected_other_count >= remaining_slots:
            break
        if record["user_id"] in selected_ids:
            continue
        records.append(record)
        selected_ids.add(record["user_id"])
        selected_other_count += 1

    existing_ids = {record["user_id"] for record in existing_users} | selected_ids
    next_index = 1

    current_new_user_count = len(selected_new_users)
    while current_new_user_count < required_new_users and len(records) < user_target:
        user_id = f"test_user_{batch_date.strftime('%Y%m%d')}_{next_index:05d}"
        next_index += 1
        if user_id in existing_ids:
            continue
        create_time = batch_date_timestamp(rng, batch_date)
        update_time = batch_date_timestamp(rng, batch_date, not_before=create_time)
        records.append(build_synthetic_user(user_id, len(records) + 1, create_time, update_time))
        existing_ids.add(user_id)
        current_new_user_count += 1

    remaining_sampled_users = sampled_new_users[required_new_users:] + sampled_other_users[selected_other_count:]
    for record in remaining_sampled_users:
        if len(records) >= user_target:
            break
        if record["user_id"] in selected_ids:
            continue
        records.append(record)
        selected_ids.add(record["user_id"])

    while len(records) < user_target:
        user_id = f"test_user_{batch_date.strftime('%Y%m%d')}_{next_index:05d}"
        next_index += 1
        if user_id in existing_ids:
            continue
        create_time = recent_timestamp(rng, batch_date, lookback_days)
        update_time = recent_timestamp(rng, batch_date, lookback_days, not_before=create_time)
        records.append(build_synthetic_user(user_id, len(records) + 1, create_time, update_time))
        existing_ids.add(user_id)
    return records[:user_target]


def generate_folders(
    existing_folders: list[dict[str, Any]],
    users: list[dict[str, Any]],
    folder_target: int,
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    records = list(existing_folders)
    existing_ids = {int(record["id"]) for record in records if record.get("id") is not None}
    existing_user_ids = {record["user_id"] for record in records}
    folder_id_cursor = int(batch_date.strftime("%Y%m%d")) * 1_000_000 + 1

    def next_folder_id() -> int:
        nonlocal folder_id_cursor
        while folder_id_cursor in existing_ids:
            folder_id_cursor += 1
        next_value = folder_id_cursor
        existing_ids.add(next_value)
        folder_id_cursor += 1
        return next_value

    user_create_map = {record["user_id"]: record.get("create_time") for record in users}

    for user in users:
        if len(records) >= folder_target:
            break
        if user["user_id"] in existing_user_ids:
            continue
        create_time = recent_timestamp(
            rng,
            batch_date,
            lookback_days,
            not_before=user_create_map.get(user["user_id"]),
        )
        update_time = recent_timestamp(rng, batch_date, lookback_days, not_before=create_time)
        records.append(
            {
                "id": next_folder_id(),
                "user_id": user["user_id"],
                "name": "默认收藏夹",
                "description": "自动生成的默认收藏夹",
                "is_public": 0,
                "movie_count": 0,
                "create_time": create_time,
                "update_time": update_time,
            }
        )
        existing_user_ids.add(user["user_id"])

    theme_names = ["高分片单", "周末想看", "反复重看", "悬疑精选", "治愈必看", "经典补片"]
    while len(records) < folder_target and users:
        user = rng.choice(users)
        create_time = recent_timestamp(
            rng,
            batch_date,
            lookback_days,
            not_before=user_create_map.get(user["user_id"]),
        )
        update_time = recent_timestamp(rng, batch_date, lookback_days, not_before=create_time)
        records.append(
            {
                "id": next_folder_id(),
                "user_id": user["user_id"],
                "name": rng.choice(theme_names),
                "description": "自动生成的主题收藏夹",
                "is_public": 1 if rng.random() < 0.35 else 0,
                "movie_count": 0,
                "create_time": create_time,
                "update_time": update_time,
            }
        )
    return records[:folder_target]


def top_up_ratings(
    existing_records: list[dict[str, Any]],
    target_count: int,
    users: list[dict[str, Any]],
    movie_ids: list[int],
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    records = list(existing_records)
    used_pairs = {(record["user_id"], int(record["movie_id"])) for record in records}
    user_create_map = {record["user_id"]: record.get("create_time") for record in users}
    max_possible = len(users) * len(movie_ids)
    target_count = min(target_count, max_possible)
    attempts = 0
    while len(records) < target_count and attempts < max(target_count * 20, 100):
        attempts += 1
        user = rng.choice(users)
        movie_id = int(rng.choice(movie_ids))
        key = (user["user_id"], movie_id)
        if key in used_pairs:
            continue
        rating_time = recent_timestamp(rng, batch_date, lookback_days, not_before=user_create_map.get(user["user_id"]))
        records.append(
            {
                "user_id": user["user_id"],
                "movie_id": movie_id,
                "rating": rng.choices([1, 2, 3, 4, 5], weights=[4, 8, 20, 36, 32], k=1)[0],
                "rating_time": rating_time,
            }
        )
        used_pairs.add(key)
    return records[:target_count]


def top_up_watched_movies(
    existing_records: list[dict[str, Any]],
    target_count: int,
    users: list[dict[str, Any]],
    movie_ids: list[int],
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    records = list(existing_records)
    used_pairs = {(record["user_id"], int(record["movie_id"])) for record in records}
    user_create_map = {record["user_id"]: record.get("create_time") for record in users}
    max_possible = len(users) * len(movie_ids)
    target_count = min(target_count, max_possible)
    attempts = 0
    while len(records) < target_count and attempts < max(target_count * 20, 100):
        attempts += 1
        user = rng.choice(users)
        movie_id = int(rng.choice(movie_ids))
        key = (user["user_id"], movie_id)
        if key in used_pairs:
            continue
        create_time = recent_timestamp(rng, batch_date, lookback_days, not_before=user_create_map.get(user["user_id"]))
        records.append(
            {
                "user_id": user["user_id"],
                "movie_id": movie_id,
                "create_time": create_time,
            }
        )
        used_pairs.add(key)
    return records[:target_count]


def top_up_favorites(
    existing_records: list[dict[str, Any]],
    target_count: int,
    folders: list[dict[str, Any]],
    movie_ids: list[int],
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    records = list(existing_records)
    folders_by_user: dict[str, list[dict[str, Any]]] = {}
    for folder in folders:
        folders_by_user.setdefault(folder["user_id"], []).append(folder)
    used_pairs = {(record["user_id"], int(record["movie_id"])) for record in records}
    max_possible = len(folders_by_user) * len(movie_ids)
    target_count = min(target_count, max_possible)
    attempts = 0
    users = list(folders_by_user.keys())
    while len(records) < target_count and attempts < max(target_count * 20, 100):
        attempts += 1
        user_id = rng.choice(users)
        movie_id = int(rng.choice(movie_ids))
        key = (user_id, movie_id)
        if key in used_pairs:
            continue
        folder = rng.choice(folders_by_user[user_id])
        create_time = recent_timestamp(rng, batch_date, lookback_days, not_before=folder.get("create_time"))
        records.append(
            {
                "user_id": user_id,
                "movie_id": movie_id,
                "folder_id": int(folder["id"]),
                "create_time": create_time,
            }
        )
        used_pairs.add(key)
    return records[:target_count]


def top_up_view_history(
    existing_records: list[dict[str, Any]],
    target_count: int,
    users: list[dict[str, Any]],
    movie_ids: list[int],
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    records = list(existing_records)
    existing_ids = {int(record["history_id"]) for record in records if record.get("history_id") is not None}
    history_id_cursor = int(batch_date.strftime("%Y%m%d")) * 10_000_000 + 1
    user_create_map = {record["user_id"]: record.get("create_time") for record in users}

    def next_history_id() -> int:
        nonlocal history_id_cursor
        while history_id_cursor in existing_ids:
            history_id_cursor += 1
        next_value = history_id_cursor
        existing_ids.add(next_value)
        history_id_cursor += 1
        return next_value

    while len(records) < target_count and users and movie_ids:
        user = rng.choice(users)
        view_time = recent_timestamp(rng, batch_date, lookback_days, not_before=user_create_map.get(user["user_id"]))
        records.append(
            {
                "history_id": next_history_id(),
                "user_id": user["user_id"],
                "movie_id": int(rng.choice(movie_ids)),
                "view_time": view_time,
            }
        )
    return records[:target_count]


def top_up_comments(
    existing_records: list[dict[str, Any]],
    target_count: int,
    users: list[dict[str, Any]],
    movie_records: list[dict[str, Any]],
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    records = list(existing_records)
    existing_ids = {int(record["comment_id"]) for record in records if record.get("comment_id") is not None}
    comment_id_cursor = int(batch_date.strftime("%Y%m%d")) * 10_000_000 + 2_000_000
    movie_name_map = {int(record["movie_id"]): record["name"] for record in movie_records}
    movie_ids = list(movie_name_map.keys())
    user_create_map = {record["user_id"]: record.get("create_time") for record in users}
    short_templates = [
        "节奏很稳，值得一看。",
        "情绪铺垫很到位，观感不错。",
        "演员状态在线，整体完成度高。",
        "故事不复杂，但细节处理得很舒服。",
        "适合周末一个人慢慢看。",
    ]
    long_templates = [
        "这部电影在人物关系和情绪推进上做得很扎实，细节经得起回看。",
        "整体叙事节奏控制得比较稳，视听表达和主题完成度都在线。",
        "虽然不是完全无瑕疵，但核心表达很清楚，后劲比预期更足。",
    ]

    def next_comment_id() -> int:
        nonlocal comment_id_cursor
        while comment_id_cursor in existing_ids:
            comment_id_cursor += 1
        next_value = comment_id_cursor
        existing_ids.add(next_value)
        comment_id_cursor += 1
        return next_value

    while len(records) < target_count and users and movie_ids:
        user = rng.choice(users)
        movie_id = int(rng.choice(movie_ids))
        movie_name = movie_name_map[movie_id]
        comment_type = 2 if rng.random() < 0.18 else 1
        comment_time = recent_timestamp(rng, batch_date, lookback_days, not_before=user_create_map.get(user["user_id"]))
        records.append(
            {
                "comment_id": next_comment_id(),
                "user_id": user["user_id"],
                "movie_id": movie_id,
                "content": rng.choice(long_templates if comment_type == 2 else short_templates),
                "votes": rng.randint(0, 80 if comment_type == 2 else 30),
                "comment_time": comment_time,
                "title": f"关于《{movie_name}》的观后感" if comment_type == 2 else None,
                "type": comment_type,
                "version": 0,
            }
        )
    return records[:target_count]


def top_up_comment_likes(
    existing_records: list[dict[str, Any]],
    target_count: int,
    users: list[dict[str, Any]],
    comments: list[dict[str, Any]],
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    records = list(existing_records)
    existing_ids = {int(record["id"]) for record in records if record.get("id") is not None}
    used_pairs = {(int(record["comment_id"]), record["user_id"]) for record in records}
    comment_like_id_cursor = int(batch_date.strftime("%Y%m%d")) * 10_000_000 + 4_000_000
    user_create_map = {record["user_id"]: record.get("create_time") for record in users}
    comment_time_map = {int(record["comment_id"]): record.get("comment_time") for record in comments}
    comment_author_map = {int(record["comment_id"]): record.get("user_id") for record in comments}
    comment_ids = [int(record["comment_id"]) for record in comments]
    max_possible = len(comment_ids) * max(len(users) - 1, 0)
    target_count = min(target_count, max_possible) if max_possible > 0 else 0

    def next_like_id() -> int:
        nonlocal comment_like_id_cursor
        while comment_like_id_cursor in existing_ids:
            comment_like_id_cursor += 1
        next_value = comment_like_id_cursor
        existing_ids.add(next_value)
        comment_like_id_cursor += 1
        return next_value

    attempts = 0
    while len(records) < target_count and attempts < max(target_count * 20, 100):
        attempts += 1
        comment_id = int(rng.choice(comment_ids))
        user = rng.choice(users)
        if comment_author_map.get(comment_id) == user["user_id"]:
            continue
        key = (comment_id, user["user_id"])
        if key in used_pairs:
            continue
        not_before_candidates = [user_create_map.get(user["user_id"]), comment_time_map.get(comment_id)]
        valid_candidates = [item for item in not_before_candidates if item is not None]
        not_before = max(valid_candidates) if valid_candidates else None
        create_time = recent_timestamp(rng, batch_date, lookback_days, not_before=not_before)
        records.append(
            {
                "id": next_like_id(),
                "comment_id": comment_id,
                "user_id": user["user_id"],
                "create_time": create_time,
            }
        )
        used_pairs.add(key)
    return records[:target_count]


def recompute_folder_movie_counts(folders: list[dict[str, Any]], favorites: list[dict[str, Any]]) -> list[dict[str, Any]]:
    movie_count_map: dict[int, int] = {}
    for record in favorites:
        folder_id = int(record["folder_id"])
        movie_count_map[folder_id] = movie_count_map.get(folder_id, 0) + 1

    normalized_records: list[dict[str, Any]] = []
    for record in folders:
        updated_record = dict(record)
        updated_record["movie_count"] = movie_count_map.get(int(record["id"]), 0)
        normalized_records.append(updated_record)
    return normalized_records


def write_ods_partition(
    spark: SparkSession,
    table_map: dict[str, dict[str, str]],
    source_table: str,
    df: DataFrame,
    batch_date: str,
) -> None:
    target_cfg = table_map[source_table]
    write_partition(df, target_cfg["target_table"], target_cfg["sink_path"], batch_date, spark)


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    generator_config = apply_arg_overrides(
        merge_nested_dict(DEFAULT_GENERATOR_CONFIG, config.get("dwd_source_generator", {})),
        args,
    )

    batch_date = parse_batch_date(args.batch_date)
    batch_date_str = batch_date.isoformat()
    sample_ratio = float(generator_config["sample_ratio"])
    lookback_days = int(generator_config["lookback_days"])
    rng = random.Random(int(generator_config["seed"]))
    jdbc_query_limit_cap = int(generator_config["jdbc_query_limit_cap"])

    spark = build_spark_session("movie-generate-dwd-source-data", config["spark"])
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS ods")

        pg_config = config["postgres"]
        table_map = build_postgres_target_map(config)

        movie_records = sample_movies(spark, pg_config, int(generator_config["movie_limit"]))
        movie_ids = [int(record["movie_id"]) for record in movie_records]

        sampled_user_records = sample_users(
            spark,
            pg_config,
            ceil_sample_count(int(generator_config["user_target"]), sample_ratio, jdbc_query_limit_cap),
        )
        user_records = generate_users(
            sampled_user_records,
            int(generator_config["user_target"]),
            int(generator_config["new_user_target"]),
            batch_date,
            lookback_days,
            rng,
        )
        user_ids = [record["user_id"] for record in user_records]

        sampled_folder_records = sample_folders(
            spark,
            pg_config,
            user_ids,
            ceil_sample_count(int(generator_config["folder_target"]), sample_ratio, jdbc_query_limit_cap),
        )
        folder_records = generate_folders(
            sampled_folder_records,
            user_records,
            int(generator_config["folder_target"]),
            batch_date,
            lookback_days,
            rng,
        )
        folder_ids = [int(record["id"]) for record in folder_records]

        favorite_records = sample_favorites(
            spark,
            pg_config,
            user_ids,
            movie_ids,
            folder_ids,
            ceil_sample_count(int(generator_config["favorite_target"]), sample_ratio, jdbc_query_limit_cap),
        )
        favorite_records = top_up_favorites(
            favorite_records,
            int(generator_config["favorite_target"]),
            folder_records,
            movie_ids,
            batch_date,
            lookback_days,
            rng,
        )
        folder_records = recompute_folder_movie_counts(folder_records, favorite_records)

        rating_records = sample_ratings(
            spark,
            pg_config,
            user_ids,
            movie_ids,
            ceil_sample_count(int(generator_config["rating_target"]), sample_ratio, jdbc_query_limit_cap),
        )
        rating_records = top_up_ratings(
            rating_records,
            int(generator_config["rating_target"]),
            user_records,
            movie_ids,
            batch_date,
            lookback_days,
            rng,
        )

        view_history_records = sample_view_history(
            spark,
            pg_config,
            user_ids,
            movie_ids,
            ceil_sample_count(int(generator_config["view_target"]), sample_ratio, jdbc_query_limit_cap),
        )
        view_history_records = top_up_view_history(
            view_history_records,
            int(generator_config["view_target"]),
            user_records,
            movie_ids,
            batch_date,
            lookback_days,
            rng,
        )

        watched_records = sample_watched_movies(
            spark,
            pg_config,
            user_ids,
            movie_ids,
            ceil_sample_count(int(generator_config["watched_target"]), sample_ratio, jdbc_query_limit_cap),
        )
        watched_records = top_up_watched_movies(
            watched_records,
            int(generator_config["watched_target"]),
            user_records,
            movie_ids,
            batch_date,
            lookback_days,
            rng,
        )

        comment_records = sample_comments(
            spark,
            pg_config,
            user_ids,
            movie_ids,
            ceil_sample_count(int(generator_config["comment_target"]), sample_ratio, jdbc_query_limit_cap),
        )
        comment_records = top_up_comments(
            comment_records,
            int(generator_config["comment_target"]),
            user_records,
            movie_records,
            batch_date,
            lookback_days,
            rng,
        )

        sampled_comment_ids = [int(record["comment_id"]) for record in comment_records]
        comment_like_records = sample_comment_likes(
            spark,
            pg_config,
            user_ids,
            sampled_comment_ids,
            ceil_sample_count(int(generator_config["comment_like_target"]), sample_ratio, jdbc_query_limit_cap),
        )
        comment_like_records = top_up_comment_likes(
            comment_like_records,
            int(generator_config["comment_like_target"]),
            user_records,
            comment_records,
            batch_date,
            lookback_days,
            rng,
        )

        movies_df = create_dataframe(spark, MOVIES_SCHEMA, movie_records)
        users_df = create_dataframe(spark, USERS_SCHEMA, user_records)
        folders_df = create_dataframe(spark, FOLDERS_SCHEMA, folder_records)
        favorites_df = create_dataframe(spark, FAVORITES_SCHEMA, favorite_records)
        ratings_df = create_dataframe(spark, RATINGS_SCHEMA, rating_records)
        comments_df = create_dataframe(spark, COMMENTS_SCHEMA, comment_records)
        comment_likes_df = create_dataframe(spark, COMMENT_LIKES_SCHEMA, comment_like_records)
        view_history_df = create_dataframe(spark, VIEW_HISTORY_SCHEMA, view_history_records)
        watched_df = create_dataframe(spark, WATCHED_SCHEMA, watched_records)

        write_ods_partition(spark, table_map, "public.movies", movies_df, batch_date_str)
        write_ods_partition(spark, table_map, "public.users", users_df, batch_date_str)
        write_ods_partition(spark, table_map, "public.favorite_folders", folders_df, batch_date_str)
        write_ods_partition(spark, table_map, "public.favorites", favorites_df, batch_date_str)
        write_ods_partition(spark, table_map, "public.ratings", ratings_df, batch_date_str)
        write_ods_partition(spark, table_map, "public.comments", comments_df, batch_date_str)
        write_ods_partition(spark, table_map, "public.comment_likes", comment_likes_df, batch_date_str)
        write_ods_partition(spark, table_map, "public.view_history", view_history_df, batch_date_str)
        write_ods_partition(spark, table_map, "public.watched_movies", watched_df, batch_date_str)

        print(
            "Generated PostgreSQL-aligned ODS snapshot test data. "
            f"dt={batch_date_str}, movies={len(movie_records)}, users={len(user_records)}, "
            f"folders={len(folder_records)}, favorites={len(favorite_records)}, ratings={len(rating_records)}, "
            f"comments={len(comment_records)}, comment_likes={len(comment_like_records)}, "
            f"view_history={len(view_history_records)}, watched={len(watched_records)}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
