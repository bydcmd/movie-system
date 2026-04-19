from __future__ import annotations

import argparse
import copy
import datetime as dt
import random
from decimal import Decimal
from typing import Any

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import types as T

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
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
    "lookback_days": 90,
    "seed": 20260412,
    "write_batch_size": 1000,
    "cleanup_existing": True,
}

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
        T.StructField("is_default", T.IntegerType(), True),
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
        T.StructField("status", T.ShortType(), True),
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
        description="Generate synthetic source data and append it directly into PostgreSQL business tables."
    )
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--batch-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Logical batch date in format YYYY-MM-DD.",
    )
    parser.add_argument("--movie-limit", type=int, default=None, help="Sampled real movie rows to reference.")
    parser.add_argument("--user-target", type=int, default=None, help="How many synthetic users to insert.")
    parser.add_argument(
        "--new-user-target",
        type=int,
        default=None,
        help="How many inserted users should register on the batch-date.",
    )
    parser.add_argument("--folder-target", type=int, default=None, help="How many favorite folders to insert.")
    parser.add_argument("--view-target", type=int, default=None, help="How many view history rows to insert.")
    parser.add_argument("--rating-target", type=int, default=None, help="How many ratings to insert.")
    parser.add_argument("--comment-target", type=int, default=None, help="How many comments to insert.")
    parser.add_argument(
        "--comment-like-target",
        type=int,
        default=None,
        help="How many comment likes to insert.",
    )
    parser.add_argument("--favorite-target", type=int, default=None, help="How many favorites to insert.")
    parser.add_argument("--watched-target", type=int, default=None, help="How many watched rows to insert.")
    parser.add_argument("--lookback-days", type=int, default=None, help="How many historical days synthetic timestamps may span.")
    parser.add_argument("--seed", type=int, default=None, help="Random seed.")
    parser.add_argument("--write-batch-size", type=int, default=None, help="JDBC batch size when appending to PostgreSQL.")
    parser.add_argument(
        "--skip-cleanup",
        action="store_true",
        help="Skip deleting previously generated test rows for the same batch-date before inserting.",
    )
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
        "lookback_days": args.lookback_days,
        "seed": args.seed,
        "write_batch_size": args.write_batch_size,
    }
    merged = dict(generator_config)
    for key, value in key_mapping.items():
        if value is not None:
            merged[key] = value
    if args.skip_cleanup:
        merged["cleanup_existing"] = False
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


def create_dataframe(spark: SparkSession, schema: T.StructType, records: list[dict[str, Any]]) -> DataFrame:
    if not records:
        return spark.createDataFrame([], schema)

    normalized_records: list[dict[str, Any]] = []
    field_names = [field.name for field in schema.fields]
    for record in records:
        normalized_records.append({field_name: record.get(field_name) for field_name in field_names})
    return spark.createDataFrame(normalized_records, schema=schema)


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


def fetch_existing_generated_user_ids(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_id_prefix: str,
) -> set[str]:
    query = f"""
        SELECT user_id
        FROM public.users
        WHERE user_id LIKE {quote_sql_string(user_id_prefix + "%")}
    """
    return {str(record["user_id"]) for record in fetch_records(spark, pg_config, query)}


def fetch_max_bigint(
    spark: SparkSession,
    pg_config: dict[str, Any],
    table_name: str,
    column_name: str,
) -> int:
    query = f"""
        SELECT COALESCE(MAX({column_name}), 0) AS max_id
        FROM {table_name}
    """
    records = fetch_records(spark, pg_config, query)
    if not records:
        return 0
    value = records[0].get("max_id")
    return 0 if value is None else int(value)


def fetch_existing_comment_keys(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_id_prefix: str,
    movie_ids: list[int],
) -> set[tuple[str, int, int]]:
    if not movie_ids:
        return set()
    query = f"""
        SELECT user_id, movie_id, type
        FROM public.comments
        WHERE user_id LIKE {quote_sql_string(user_id_prefix + "%")}
          AND movie_id IN {in_clause_bigint(movie_ids)}
          AND type IS NOT NULL
    """
    return {
        (str(record["user_id"]), int(record["movie_id"]), int(record["type"]))
        for record in fetch_records(spark, pg_config, query)
        if record.get("user_id") is not None and record.get("movie_id") is not None and record.get("type") is not None
    }


def fetch_existing_user_movie_keys(
    spark: SparkSession,
    pg_config: dict[str, Any],
    table_name: str,
    user_id_prefix: str,
    movie_ids: list[int],
) -> set[tuple[str, int]]:
    if not movie_ids:
        return set()
    query = f"""
        SELECT user_id, movie_id
        FROM {table_name}
        WHERE user_id LIKE {quote_sql_string(user_id_prefix + "%")}
          AND movie_id IN {in_clause_bigint(movie_ids)}
    """
    return {
        (str(record["user_id"]), int(record["movie_id"]))
        for record in fetch_records(spark, pg_config, query)
        if record.get("user_id") is not None and record.get("movie_id") is not None
    }


def write_to_postgres(
    df: DataFrame,
    pg_config: dict[str, Any],
    table_name: str,
    batch_size: int,
) -> None:
    if df.rdd.isEmpty():
        return

    (
        df.write.format("jdbc")
        .option("url", pg_config["jdbc_url"])
        .option("dbtable", table_name)
        .option("driver", pg_config.get("driver", "org.postgresql.Driver"))
        .option("user", pg_config["user"])
        .option("password", pg_config["password"])
        .option("batchsize", str(batch_size))
        .mode("append")
        .save()
    )


def cleanup_generated_batch_data(
    spark: SparkSession,
    pg_config: dict[str, Any],
    batch_date: dt.date,
) -> dict[str, int]:
    user_prefix = f"test_user_{batch_date.strftime('%Y%m%d')}_"
    user_like = quote_sql_string(user_prefix + "%")

    jvm = spark.sparkContext._gateway.jvm
    driver = pg_config.get("driver", "org.postgresql.Driver")
    jvm.java.lang.Class.forName(driver)

    statements: list[tuple[str, str]] = [
        (
            "comment_likes",
            f"""
            DELETE FROM public.comment_likes
            WHERE user_id LIKE {user_like}
               OR comment_id IN (
                    SELECT comment_id
                    FROM public.comments
                    WHERE user_id LIKE {user_like}
               )
            """,
        ),
        ("favorites", f"DELETE FROM public.favorites WHERE user_id LIKE {user_like}"),
        ("ratings", f"DELETE FROM public.ratings WHERE user_id LIKE {user_like}"),
        ("view_history", f"DELETE FROM public.view_history WHERE user_id LIKE {user_like}"),
        ("watched_movies", f"DELETE FROM public.watched_movies WHERE user_id LIKE {user_like}"),
        ("comments", f"DELETE FROM public.comments WHERE user_id LIKE {user_like}"),
        ("favorite_folders", f"DELETE FROM public.favorite_folders WHERE user_id LIKE {user_like}"),
        ("users", f"DELETE FROM public.users WHERE user_id LIKE {user_like}"),
    ]

    connection = None
    statement = None
    deleted_counts: dict[str, int] = {}
    try:
        connection = jvm.java.sql.DriverManager.getConnection(
            pg_config["jdbc_url"],
            pg_config["user"],
            pg_config["password"],
        )
        connection.setAutoCommit(False)
        statement = connection.createStatement()
        for label, sql_text in statements:
            deleted_counts[label] = int(statement.executeUpdate(sql_text))
        connection.commit()
        return deleted_counts
    except Exception:
        if connection is not None:
            connection.rollback()
        raise
    finally:
        if statement is not None:
            statement.close()
        if connection is not None:
            connection.close()


def sync_postgres_sequence(
    spark: SparkSession,
    pg_config: dict[str, Any],
    sequence_name: str,
    table_name: str,
    column_name: str,
) -> None:
    query = f"""
        SELECT setval(
          {quote_sql_string(sequence_name)},
          GREATEST((SELECT COALESCE(MAX({column_name}), 0) FROM {table_name}), 1),
          true
        ) AS sequence_value
    """
    fetch_records(spark, pg_config, query)


def generate_users(
    user_target: int,
    new_user_target: int,
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
    existing_generated_user_ids: set[str],
) -> list[dict[str, Any]]:
    required_new_users = min(max(new_user_target, 0), max(user_target, 0))
    records: list[dict[str, Any]] = []
    existing_ids = set(existing_generated_user_ids)
    next_index = 1

    current_new_user_count = 0
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
    start_folder_id: int,
) -> list[dict[str, Any]]:
    records = list(existing_folders)
    existing_ids = {int(record["id"]) for record in records if record.get("id") is not None}
    existing_user_ids = {record["user_id"] for record in records}
    folder_id_cursor = max(int(start_folder_id), 0) + 1

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
                "is_default": 1,
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
                "is_default": 0,
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
    existing_pairs: set[tuple[str, int]],
) -> list[dict[str, Any]]:
    records = list(existing_records)
    used_pairs = {(record["user_id"], int(record["movie_id"])) for record in records} | set(existing_pairs)
    user_create_map = {record["user_id"]: record.get("create_time") for record in users}
    max_possible = len(users) * len(movie_ids)
    target_count = min(target_count, max_possible - len(existing_pairs))
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
    existing_pairs: set[tuple[str, int]],
) -> list[dict[str, Any]]:
    records = list(existing_records)
    used_pairs = {(record["user_id"], int(record["movie_id"])) for record in records} | set(existing_pairs)
    user_create_map = {record["user_id"]: record.get("create_time") for record in users}
    max_possible = len(users) * len(movie_ids)
    target_count = min(target_count, max_possible - len(existing_pairs))
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
    start_history_id: int,
    existing_pairs: set[tuple[str, int]],
) -> list[dict[str, Any]]:
    records = list(existing_records)
    existing_ids = {int(record["history_id"]) for record in records if record.get("history_id") is not None}
    used_pairs = {(record["user_id"], int(record["movie_id"])) for record in records} | set(existing_pairs)
    history_id_cursor = max(int(start_history_id), 0) + 1
    user_create_map = {record["user_id"]: record.get("create_time") for record in users}

    def next_history_id() -> int:
        nonlocal history_id_cursor
        while history_id_cursor in existing_ids:
            history_id_cursor += 1
        next_value = history_id_cursor
        existing_ids.add(next_value)
        history_id_cursor += 1
        return next_value

    max_possible = len(users) * len(movie_ids)
    target_count = min(target_count, max_possible - len(existing_pairs))
    attempts = 0
    while len(records) < target_count and users and movie_ids and attempts < max(target_count * 20, 100):
        attempts += 1
        user = rng.choice(users)
        movie_id = int(rng.choice(movie_ids))
        key = (user["user_id"], movie_id)
        if key in used_pairs:
            continue
        view_time = recent_timestamp(rng, batch_date, lookback_days, not_before=user_create_map.get(user["user_id"]))
        records.append(
            {
                "history_id": next_history_id(),
                "user_id": user["user_id"],
                "movie_id": movie_id,
                "view_time": view_time,
            }
        )
        used_pairs.add(key)
    return records[:target_count]


def top_up_comments(
    existing_records: list[dict[str, Any]],
    target_count: int,
    users: list[dict[str, Any]],
    movie_records: list[dict[str, Any]],
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
    start_comment_id: int,
    existing_comment_keys: set[tuple[str, int, int]],
) -> list[dict[str, Any]]:
    records = list(existing_records)
    existing_ids = {int(record["comment_id"]) for record in records if record.get("comment_id") is not None}
    used_keys = {
        (str(record["user_id"]), int(record["movie_id"]), int(record["type"]))
        for record in records
        if record.get("user_id") is not None and record.get("movie_id") is not None and record.get("type") is not None
    } | set(existing_comment_keys)
    comment_id_cursor = max(int(start_comment_id), 0) + 1
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

    max_possible = max(0, len(users) * len(movie_ids) * 2 - len(used_keys))
    target_count = min(target_count, len(records) + max_possible)
    attempts = 0
    while len(records) < target_count and users and movie_ids and attempts < max(target_count * 40, 200):
        attempts += 1
        user = rng.choice(users)
        movie_id = int(rng.choice(movie_ids))
        movie_name = movie_name_map[movie_id]
        comment_type = 2 if rng.random() < 0.18 else 1
        unique_key = (user["user_id"], movie_id, comment_type)
        if unique_key in used_keys:
            continue
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
                "status": 2,
            }
        )
        used_keys.add(unique_key)
    return records[:target_count]


def top_up_comment_likes(
    existing_records: list[dict[str, Any]],
    target_count: int,
    users: list[dict[str, Any]],
    comments: list[dict[str, Any]],
    batch_date: dt.date,
    lookback_days: int,
    rng: random.Random,
    start_like_id: int,
) -> list[dict[str, Any]]:
    records = list(existing_records)
    existing_ids = {int(record["id"]) for record in records if record.get("id") is not None}
    used_pairs = {(int(record["comment_id"]), record["user_id"]) for record in records}
    comment_like_id_cursor = max(int(start_like_id), 0) + 1
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


def run() -> None:
    args = parse_args()
    config = load_config(args.config)
    generator_config = apply_arg_overrides(
        merge_nested_dict(DEFAULT_GENERATOR_CONFIG, config.get("dwd_source_generator", {})),
        args,
    )

    batch_date = parse_batch_date(args.batch_date)
    batch_date_str = batch_date.isoformat()
    lookback_days = int(generator_config["lookback_days"])
    rng = random.Random(int(generator_config["seed"]))
    write_batch_size = int(generator_config["write_batch_size"])
    cleanup_existing = bool(generator_config.get("cleanup_existing", True))
    if write_batch_size <= 0:
        raise ValueError(f"Invalid write_batch_size: {write_batch_size}")

    spark = build_spark_session("movie-generate-dwd-source-data", config["spark"])
    try:
        pg_config = config["postgres"]

        cleanup_summary: dict[str, int] | None = None
        if cleanup_existing:
            cleanup_summary = cleanup_generated_batch_data(spark, pg_config, batch_date)
            print(
                "Cleaned existing generated PostgreSQL rows. "
                + ", ".join(f"{key}={value}" for key, value in cleanup_summary.items())
            )

        movie_records = sample_movies(spark, pg_config, int(generator_config["movie_limit"]))
        movie_ids = [int(record["movie_id"]) for record in movie_records]

        existing_generated_user_ids = fetch_existing_generated_user_ids(
            spark,
            pg_config,
            f"test_user_{batch_date.strftime('%Y%m%d')}_",
        )
        user_records = generate_users(
            int(generator_config["user_target"]),
            int(generator_config["new_user_target"]),
            batch_date,
            lookback_days,
            rng,
            existing_generated_user_ids,
        )

        folder_records = generate_folders(
            [],
            user_records,
            int(generator_config["folder_target"]),
            batch_date,
            lookback_days,
            rng,
            fetch_max_bigint(spark, pg_config, "public.favorite_folders", "id"),
        )

        favorite_records = top_up_favorites(
            [],
            int(generator_config["favorite_target"]),
            folder_records,
            movie_ids,
            batch_date,
            lookback_days,
            rng,
        )
        folder_records = recompute_folder_movie_counts(folder_records, favorite_records)

        rating_records = top_up_ratings(
            [],
            int(generator_config["rating_target"]),
            user_records,
            movie_ids,
            batch_date,
            lookback_days,
            rng,
            fetch_existing_user_movie_keys(
                spark,
                pg_config,
                "public.ratings",
                f"test_user_{batch_date.strftime('%Y%m%d')}_",
                movie_ids,
            ),
        )

        view_history_records = top_up_view_history(
            [],
            int(generator_config["view_target"]),
            user_records,
            movie_ids,
            batch_date,
            lookback_days,
            rng,
            fetch_max_bigint(spark, pg_config, "public.view_history", "history_id"),
            fetch_existing_user_movie_keys(
                spark,
                pg_config,
                "public.view_history",
                f"test_user_{batch_date.strftime('%Y%m%d')}_",
                movie_ids,
            ),
        )

        watched_records = top_up_watched_movies(
            [],
            int(generator_config["watched_target"]),
            user_records,
            movie_ids,
            batch_date,
            lookback_days,
            rng,
            fetch_existing_user_movie_keys(
                spark,
                pg_config,
                "public.watched_movies",
                f"test_user_{batch_date.strftime('%Y%m%d')}_",
                movie_ids,
            ),
        )

        comment_records = top_up_comments(
            [],
            int(generator_config["comment_target"]),
            user_records,
            movie_records,
            batch_date,
            lookback_days,
            rng,
            fetch_max_bigint(spark, pg_config, "public.comments", "comment_id"),
            fetch_existing_comment_keys(
                spark,
                pg_config,
                f"test_user_{batch_date.strftime('%Y%m%d')}_",
                movie_ids,
            ),
        )

        comment_like_records = top_up_comment_likes(
            [],
            int(generator_config["comment_like_target"]),
            user_records,
            comment_records,
            batch_date,
            lookback_days,
            rng,
            fetch_max_bigint(spark, pg_config, "public.comment_likes", "id"),
        )

        users_df = create_dataframe(spark, USERS_SCHEMA, user_records)
        folders_df = create_dataframe(spark, FOLDERS_SCHEMA, folder_records)
        favorites_df = create_dataframe(spark, FAVORITES_SCHEMA, favorite_records)
        ratings_df = create_dataframe(spark, RATINGS_SCHEMA, rating_records)
        comments_df = create_dataframe(spark, COMMENTS_SCHEMA, comment_records)
        comment_likes_df = create_dataframe(spark, COMMENT_LIKES_SCHEMA, comment_like_records)
        view_history_df = create_dataframe(spark, VIEW_HISTORY_SCHEMA, view_history_records)
        watched_df = create_dataframe(spark, WATCHED_SCHEMA, watched_records)

        write_to_postgres(users_df, pg_config, "public.users", write_batch_size)
        write_to_postgres(folders_df, pg_config, "public.favorite_folders", write_batch_size)
        write_to_postgres(favorites_df, pg_config, "public.favorites", write_batch_size)
        write_to_postgres(ratings_df, pg_config, "public.ratings", write_batch_size)
        write_to_postgres(comments_df, pg_config, "public.comments", write_batch_size)
        write_to_postgres(comment_likes_df, pg_config, "public.comment_likes", write_batch_size)
        write_to_postgres(view_history_df, pg_config, "public.view_history", write_batch_size)
        write_to_postgres(watched_df, pg_config, "public.watched_movies", write_batch_size)

        sync_postgres_sequence(spark, pg_config, "favorite_folders_id_seq", "public.favorite_folders", "id")
        sync_postgres_sequence(spark, pg_config, "comments_comment_id_seq", "public.comments", "comment_id")
        sync_postgres_sequence(spark, pg_config, "comment_likes_id_seq", "public.comment_likes", "id")
        sync_postgres_sequence(spark, pg_config, "view_history_history_id_seq", "public.view_history", "history_id")

        print(
            "Inserted synthetic PostgreSQL source data. "
            f"batch_date={batch_date_str}, referenced_movies={len(movie_records)}, inserted_users={len(user_records)}, "
            f"folders={len(folder_records)}, favorites={len(favorite_records)}, ratings={len(rating_records)}, "
            f"comments={len(comment_records)}, comment_likes={len(comment_like_records)}, "
            f"view_history={len(view_history_records)}, watched={len(watched_records)}"
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
