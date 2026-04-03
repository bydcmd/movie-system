from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import re
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable
from zoneinfo import ZoneInfo

from pyspark.sql import SparkSession

import _bootstrap  # noqa: F401

from utils.config_loader import load_config
from utils.spark_factory import build_spark_session

DEFAULT_FIXTURE_DIR = "fixtures/dwd_user_event_source_data"
DEFAULT_WRITE_MODE = "both"
DEFAULT_USER_COUNT = 4
DEFAULT_MOVIE_COUNT = 6
DEFAULT_EVENTS_PER_TYPE = 2
DISPLAY_REGISTERED_USER_CAP = 24
EXTRA_LOGIN_USER_CAP = 2
SQL_CHUNK_SIZE = 200
MAX_IDENTIFIER_TAG_LEN = 20
DEFAULT_VALIDATION_MODE = "warn"
SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")

EVENT_ORDER = [
    "user_register",
    "search",
    "view_history",
    "rating",
    "comment",
    "comment_like",
    "favorite",
    "watched",
    "favorite_folder_action",
    "user_login",
]

EVENT_TIME_GROUP_INDEX = {
    "user_register": 7,
    "search": 8,
    "view_history": 9,
    "rating": 10,
    "comment": 11,
    "comment_like": 12,
    "favorite": 13,
    "watched": 14,
    "favorite_folder_action": 15,
    "user_login": 16,
}

TOPIC_BY_EVENT_TYPE = {
    "view_history": "movie-view-history",
    "rating": "movie-rating-events",
    "comment": "movie-comment-events",
    "comment_like": "movie-comment-like-events",
    "favorite": "movie-favorite-events",
    "watched": "movie-watched-events",
    "favorite_folder_action": "movie-favorite-folder-action-events",
    "search": "movie-search-events",
    "user_register": "movie-user-register-events",
    "user_login": "movie-user-login-events",
}

TABLE_ORDER = [
    "public.users",
    "public.favorite_folders",
    "public.comments",
    "public.ratings",
    "public.favorites",
    "public.view_history",
    "public.watched_movies",
    "public.comment_likes",
]

TABLE_COLUMNS: dict[str, list[str]] = {
    "public.users": [
        "user_id",
        "user_nickname",
        "user_password",
        "user_avatar",
        "user_url",
        "role",
        "status",
        "password_version",
        "email",
        "create_time",
        "update_time",
    ],
    "public.favorite_folders": [
        "id",
        "user_id",
        "name",
        "description",
        "is_public",
        "movie_count",
        "create_time",
        "update_time",
    ],
    "public.comments": [
        "comment_id",
        "user_id",
        "movie_id",
        "content",
        "votes",
        "comment_time",
        "title",
        "type",
        "version",
        "status",
    ],
    "public.ratings": [
        "user_id",
        "movie_id",
        "rating",
        "rating_time",
    ],
    "public.favorites": [
        "user_id",
        "movie_id",
        "folder_id",
        "create_time",
    ],
    "public.view_history": [
        "history_id",
        "user_id",
        "movie_id",
        "view_time",
    ],
    "public.watched_movies": [
        "user_id",
        "movie_id",
        "create_time",
    ],
    "public.comment_likes": [
        "id",
        "comment_id",
        "user_id",
        "create_time",
    ],
}

TABLE_COLUMN_TYPES: dict[str, dict[str, str]] = {
    "public.users": {
        "create_time": "timestamp",
        "update_time": "timestamp",
    },
    "public.favorite_folders": {
        "create_time": "timestamp",
        "update_time": "timestamp",
    },
    "public.comments": {
        "comment_time": "timestamp",
    },
    "public.ratings": {
        "rating_time": "timestamp",
    },
    "public.favorites": {
        "create_time": "timestamp",
    },
    "public.view_history": {
        "view_time": "timestamp",
    },
    "public.watched_movies": {
        "create_time": "timestamp",
    },
    "public.comment_likes": {
        "create_time": "timestamp",
    },
}

SEQUENCE_RESET_SQL = {
    "public.favorite_folders": "SELECT setval('public.favorite_folders_id_seq', COALESCE((SELECT MAX(id) FROM public.favorite_folders), 1), true);",
    "public.comments": "SELECT setval('public.comments_comment_id_seq', COALESCE((SELECT MAX(comment_id) FROM public.comments), 1), true);",
    "public.view_history": "SELECT setval('public.view_history_history_id_seq', COALESCE((SELECT MAX(history_id) FROM public.view_history), 1), true);",
    "public.comment_likes": "SELECT setval('public.comment_likes_id_seq', COALESCE((SELECT MAX(id) FROM public.comment_likes), 1), true);",
}

RATING_OPERATIONS = ["CREATE", "UPDATE", "DELETE", "CLEAR"]
COMMENT_OPERATIONS = ["CREATE", "UPDATE", "DELETE"]
COMMENT_LIKE_OPERATIONS = ["LIKE", "UNLIKE"]
FAVORITE_OPERATIONS = ["ADD", "REMOVE"]
FOLDER_OPERATIONS = ["CREATE", "UPDATE", "SHARE", "DELETE"]


@dataclass(frozen=True)
class TableDataset:
    table_name: str
    rows: list[dict[str, Any]]
    cleanup_statements: list[str]
    insert_statements: list[str]
    post_statements: list[str]


@dataclass(frozen=True)
class KafkaRecord:
    topic: str
    key: str
    value: str
    event_type: str


@dataclass(frozen=True)
class GeneratedDataset:
    batch_date: str
    batch_tag: str
    pg_tables: list[TableDataset]
    kafka_records: list[KafkaRecord]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate deterministic PostgreSQL and Kafka source data for dwd_user_event_wide_di."
    )
    parser.add_argument("--config", required=True, help="Path of ETL json config.")
    parser.add_argument(
        "--batch-date",
        default=dt.date.today().strftime("%Y-%m-%d"),
        help="Business date in format YYYY-MM-DD.",
    )
    parser.add_argument(
        "--user-count",
        type=int,
        default=DEFAULT_USER_COUNT,
        help=f"Number of existing users to sample from PostgreSQL, default: {DEFAULT_USER_COUNT}.",
    )
    parser.add_argument(
        "--movie-count",
        type=int,
        default=DEFAULT_MOVIE_COUNT,
        help=f"Number of existing movies to sample from PostgreSQL, default: {DEFAULT_MOVIE_COUNT}.",
    )
    parser.add_argument(
        "--events-per-type",
        type=int,
        default=DEFAULT_EVENTS_PER_TYPE,
        help=f"Number of Kafka events to generate for each event type, default: {DEFAULT_EVENTS_PER_TYPE}.",
    )
    parser.add_argument(
        "--write-mode",
        choices=["direct", "fixtures", "both"],
        default=DEFAULT_WRITE_MODE,
        help="direct writes PostgreSQL and Kafka, fixtures exports SQL/JSONL, both does both.",
    )
    parser.add_argument(
        "--fixture-dir",
        default=DEFAULT_FIXTURE_DIR,
        help=f"Fixture output directory, default: {DEFAULT_FIXTURE_DIR}.",
    )
    parser.add_argument(
        "--batch-tag",
        default="",
        help="Optional deterministic tag used in generated identifiers and cleanup scope.",
    )
    parser.add_argument(
        "--display-registered-user-cap",
        type=int,
        default=DISPLAY_REGISTERED_USER_CAP,
        help=f"Maximum number of registered users to display, default: {DISPLAY_REGISTERED_USER_CAP}.",
    )
    parser.add_argument(
        "--extra-login-user-cap",
        type=int,
        default=EXTRA_LOGIN_USER_CAP,
        help=f"Additional existing users to include in login events, default: {EXTRA_LOGIN_USER_CAP}.",
    )
    parser.add_argument(
        "--sql-chunk-size",
        type=int,
        default=SQL_CHUNK_SIZE,
        help=f"Chunk size for SQL batch operations, default: {SQL_CHUNK_SIZE}.",
    )
    parser.add_argument(
        "--max-identifier-tag-len",
        type=int,
        default=MAX_IDENTIFIER_TAG_LEN,
        help=f"Maximum length for normalized batch tag, default: {MAX_IDENTIFIER_TAG_LEN}.",
    )
    parser.add_argument(
        "--validation-mode",
        choices=["none", "warn", "error"],
        default=DEFAULT_VALIDATION_MODE,
        help=f"Validation strictness: none (skip), warn (print issues), error (raise on issues). Default: {DEFAULT_VALIDATION_MODE}.",
    )
    parser.add_argument(
        "--rating-bias",
        type=float,
        default=0.0,
        help="Bias to apply to all ratings (positive for higher ratings, negative for lower). Range: -2.0 to 2.0.",
    )
    parser.add_argument(
        "--spark-parallelism",
        type=int,
        default=None,
        help="Spark parallelism (sets spark.sql.shuffle.partitions and spark.default.parallelism). Default: use Spark defaults.",
    )
    return parser.parse_args()


def normalize_batch_tag(value: str, max_length: int = MAX_IDENTIFIER_TAG_LEN) -> str:
    normalized = re.sub(r"[^0-9A-Za-z]+", "_", value.strip().lower()).strip("_")
    if not normalized:
        raise ValueError("batch_tag must contain at least one alphanumeric character after normalization.")
    return normalized[:max_length]


def validate_positive_int(name: str, value: int) -> None:
    if value <= 0:
        raise ValueError(f"{name} must be a positive integer, got {value}.")


def validate_rating_bias(value: float) -> None:
    if not -2.0 <= value <= 2.0:
        raise ValueError(f"rating_bias must be between -2.0 and 2.0, got {value}.")


def stable_bigint(seed: str) -> int:
    return int(hashlib.sha1(seed.encode("utf-8")).hexdigest()[:15], 16)


def deterministic_rating(seed: str, bias: float = 0.0) -> float:
    """Generate a deterministic rating between 1.0 and 5.0 with normal distribution."""
    # Generate two uniform random numbers from hash
    hash1 = int(hashlib.sha256(f"{seed}:1".encode()).hexdigest()[:16], 16)
    hash2 = int(hashlib.sha256(f"{seed}:2".encode()).hexdigest()[:16], 16)

    # Convert to (0, 1) range (avoiding exact 0)
    u1 = hash1 / (2**64 - 1) + 1e-12
    u2 = hash2 / (2**64 - 1) + 1e-12

    # Box-Muller transform for standard normal
    z0 = math.sqrt(-2.0 * math.log(u1)) * math.cos(2.0 * math.pi * u2)

    # Transform to mean=3.5, std=1.0, apply bias
    rating = 3.5 + z0 * 1.0 + bias

    # Clamp to [1.0, 5.0] and round to 1 decimal
    rating = max(1.0, min(5.0, rating))
    return round(rating, 1)


def seeded_uuid(batch_tag: str, event_type: str, index: int, key: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"{batch_tag}:{event_type}:{index}:{key}"))


def event_datetime(batch_date: dt.date, event_group_index: int, item_index: int) -> dt.datetime:
    base = dt.datetime.combine(batch_date, dt.time(hour=12, minute=0, tzinfo=SHANGHAI_TZ))
    return base + dt.timedelta(minutes=event_group_index * 7, seconds=item_index * 17)


def user_event_datetime(batch_date: dt.date, user_id: str, event_type: str, item_index: int, batch_tag: str = "") -> dt.datetime:
    """
    Generate deterministic event datetime for a user, ensuring chronological order
    of event types for the same user.

    Timeline order for same user: register → search → view → rating → comment → favorite → watched
    """
    base = dt.datetime.combine(batch_date, dt.time(hour=12, minute=0, tzinfo=SHANGHAI_TZ))

    # User-specific base offset (0-59 minutes) based on user_id and batch_tag hash
    user_seed = f"{batch_tag}:{user_id}" if batch_tag else user_id
    user_hash = stable_bigint(user_seed)
    user_offset_minutes = user_hash % 60

    # Event type offset based on EVENT_TIME_GROUP_INDEX
    event_group_index = EVENT_TIME_GROUP_INDEX.get(event_type, 0)

    # Small offset for item_index within same event type
    item_offset_seconds = item_index * 17

    # Calculate total offset
    total_minutes = user_offset_minutes + event_group_index * 7
    return base + dt.timedelta(minutes=total_minutes, seconds=item_offset_seconds)


def ensure_after_anchor(
    value: dt.datetime,
    anchor: dt.datetime | None,
    delta: dt.timedelta = dt.timedelta(seconds=17),
) -> dt.datetime:
    if anchor is None or value > anchor:
        return value
    return anchor + delta


def format_timestamp(value: dt.datetime) -> str:
    if value.tzinfo is not None:
        value = value.astimezone(SHANGHAI_TZ).replace(tzinfo=None)
    return value.strftime("%Y-%m-%d %H:%M:%S")


def sql_escape_string(value: str) -> str:
    return value.replace("'", "''")


def sql_literal(value: Any, type_hint: str = "") -> str:
    if value is None:
        return "NULL"
    if type_hint in {"text", "string", "varchar"}:
        if not isinstance(value, str):
            raise TypeError(f"Expected string for text literal, got {type(value)!r}")
        return f"'{sql_escape_string(value)}'"
    if type_hint == "timestamp":
        if not isinstance(value, dt.datetime):
            raise TypeError(f"Expected datetime for timestamp literal, got {type(value)!r}")
        return f"TIMESTAMP '{format_timestamp(value)}'"
    if type_hint == "date":
        if isinstance(value, dt.datetime):
            value = value.date()
        if not isinstance(value, dt.date):
            raise TypeError(f"Expected date for date literal, got {type(value)!r}")
        return f"DATE '{value.isoformat()}'"
    if type_hint == "jsonb":
        payload = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
        return f"'{sql_escape_string(payload)}'::jsonb"
    if isinstance(value, str):
        return f"'{sql_escape_string(value)}'"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    return str(value)


def chunked(values: list[Any], size: int) -> Iterable[list[Any]]:
    for start in range(0, len(values), size):
        yield values[start : start + size]


def build_delete_in_statements(
    table_name: str,
    column_name: str,
    values: list[Any],
    type_hint: str = "",
) -> list[str]:
    statements: list[str] = []
    unique_values: list[Any] = list(dict.fromkeys(values))
    for value_chunk in chunked(unique_values, 500):
        literals = ", ".join(sql_literal(item, type_hint) for item in value_chunk)
        statements.append(f"DELETE FROM {table_name} WHERE {column_name} IN ({literals});")
    return statements


def build_insert_statements(table_name: str, rows: list[dict[str, Any]], args: argparse.Namespace) -> list[str]:
    if not rows:
        return []

    columns = TABLE_COLUMNS[table_name]
    column_types = TABLE_COLUMN_TYPES.get(table_name, {})
    statements: list[str] = []
    for row_chunk in chunked(rows, args.sql_chunk_size):
        value_lines: list[str] = []
        for row in row_chunk:
            literals = [sql_literal(row[column_name], column_types.get(column_name, "")) for column_name in columns]
            value_lines.append(f"({', '.join(literals)})")
        statements.append(
            f"INSERT INTO {table_name} ({', '.join(columns)}) VALUES\n  " + ",\n  ".join(value_lines) + ";"
        )
    return statements


def pick_cycle(items: list[dict[str, Any]], index: int) -> dict[str, Any]:
    return items[index % len(items)]


def load_existing_users(
    spark: SparkSession,
    pg_config: dict[str, Any],
    user_count: int,
) -> list[dict[str, Any]]:
    query = (
        "(\n"
        "  SELECT user_id, user_nickname, user_password, user_avatar, user_url,\n"
        "         role, status, password_version, email, create_time, update_time\n"
        "  FROM public.users\n"
        "  WHERE user_id IS NOT NULL\n"
        "    AND COALESCE(status, 0) = 0\n"
        "  ORDER BY COALESCE(create_time, TIMESTAMP '1970-01-01 00:00:00'), user_id\n"
        f"  LIMIT {user_count}\n"
        ") src"
    )
    dataframe = (
        spark.read.format("jdbc")
        .option("url", pg_config["jdbc_url"])
        .option("dbtable", query)
        .option("driver", pg_config.get("driver", "org.postgresql.Driver"))
        .option("user", pg_config["user"])
        .option("password", pg_config["password"])
        .option("fetchsize", str(pg_config.get("fetch_size", 1000)))
        .load()
    )

    rows: list[dict[str, Any]] = []
    for row in dataframe.collect():
        row_dict = row.asDict()
        user_id = row_dict.get("user_id")
        if not user_id:
            continue
        rows.append(
            {
                "user_id": str(user_id),
                "user_nickname": row_dict.get("user_nickname") or f"Sampled User {len(rows) + 1}",
                "user_password": row_dict.get("user_password")
                or "$2a$10$lPHc.uX1uT4Q/54HYO9DfO8B4TCOJYAZGsaemn0pLxA3OoHQeOd5S",
                "user_avatar": row_dict.get("user_avatar"),
                "user_url": row_dict.get("user_url"),
                "role": int(row_dict.get("role")) if row_dict.get("role") is not None else 1,
                "status": int(row_dict.get("status")) if row_dict.get("status") is not None else 0,
                "password_version": (
                    int(row_dict.get("password_version")) if row_dict.get("password_version") is not None else 1
                ),
                "email": row_dict.get("email"),
                "create_time": row_dict.get("create_time"),
                "update_time": row_dict.get("update_time"),
                "_register_time": None,
                "_login_time": None,
            }
        )

    if len(rows) < user_count:
        raise ValueError(
            f"Not enough active users in public.users to sample. requested={user_count}, actual={len(rows)}"
        )
    return rows


def build_registered_user_rows(batch_tag: str, batch_date: dt.date, user_count: int) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for index in range(user_count):
        user_id = f"seed_{batch_tag}_reg_u{index + 1:03d}"
        register_time = event_datetime(batch_date, EVENT_TIME_GROUP_INDEX["user_register"], index)
        login_time = event_datetime(batch_date, EVENT_TIME_GROUP_INDEX["user_login"], index)
        rows.append(
            {
                "user_id": user_id,
                "user_nickname": f"Seed Registered User {index + 1}",
                "user_password": "$2a$10$lPHc.uX1uT4Q/54HYO9DfO8B4TCOJYAZGsaemn0pLxA3OoHQeOd5S",
                "user_avatar": f"https://example.test/avatar/{batch_tag}/registered/{index + 1}.png",
                "user_url": f"https://example.test/user/{user_id}",
                "role": 1,
                "status": 0,
                "password_version": 1,
                "email": f"{user_id}@example.test",
                "create_time": register_time,
                "update_time": login_time,
                "_register_time": register_time,
                "_login_time": login_time,
            }
        )
    return rows


def load_existing_movies(
    spark: SparkSession,
    pg_config: dict[str, Any],
    movie_count: int,
    batch_date: dt.date,
) -> list[dict[str, Any]]:
    query = (
        "(\n"
        "  SELECT movie_id, name, genres, year, regions\n"
        "  FROM public.movies\n"
        "  WHERE movie_id IS NOT NULL\n"
        "  ORDER BY movie_id\n"
        f"  LIMIT {movie_count}\n"
        ") src"
    )
    dataframe = (
        spark.read.format("jdbc")
        .option("url", pg_config["jdbc_url"])
        .option("dbtable", query)
        .option("driver", pg_config.get("driver", "org.postgresql.Driver"))
        .option("user", pg_config["user"])
        .option("password", pg_config["password"])
        .option("fetchsize", str(pg_config.get("fetch_size", 1000)))
        .load()
    )

    rows: list[dict[str, Any]] = []
    for row in dataframe.collect():
        row_dict = row.asDict()
        movie_id = row_dict.get("movie_id")
        if movie_id is None:
            continue
        year = row_dict.get("year")
        rows.append(
            {
                "movie_id": int(movie_id),
                "name": (row_dict.get("name") or f"Movie {movie_id}").strip(),
                "genres": (row_dict.get("genres") or "Unknown").strip(),
                "year": int(year) if year is not None else batch_date.year,
                "regions": (row_dict.get("regions") or "Unknown").strip(),
            }
        )

    if len(rows) < movie_count:
        raise ValueError(
            f"Not enough movies in public.movies to sample. requested={movie_count}, actual={len(rows)}"
        )
    return rows


def build_folder_rows(batch_tag: str, batch_date: dt.date, users: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for index, user in enumerate(users):
        created_at = ensure_after_anchor(event_datetime(batch_date, 1, index), user.get("_register_time"))
        rows.append(
            {
                "id": stable_bigint(f"{batch_tag}:folder:{index}"),
                "user_id": user["user_id"],
                "name": f"seed_folder_{index + 1}_{batch_tag}",
                "description": f"Seed favorite folder {index + 1} for {batch_tag}",
                "is_public": index % 2,
                "movie_count": 0,
                "create_time": created_at,
                "update_time": created_at + dt.timedelta(minutes=15),
            }
        )
    return rows


def build_user_movie_pairs(users: list[dict[str, Any]], movies: list[dict[str, Any]]) -> list[tuple[dict[str, Any], dict[str, Any]]]:
    # Keep the early rows spread across more users so small entity_count values
    # still cover a visible user cohort instead of collapsing onto one user.
    return [(user, movie) for movie in movies for user in users]


def build_comment_rows(
    batch_tag: str,
    batch_date: dt.date,
    users: list[dict[str, Any]],
    movies: list[dict[str, Any]],
    entity_count: int,
) -> list[dict[str, Any]]:
    combos: list[tuple[dict[str, Any], dict[str, Any], int]] = []
    for comment_type in (1, 2):
        for movie in movies:
            for user in users:
                combos.append((user, movie, comment_type))

    rows: list[dict[str, Any]] = []
    for index in range(min(entity_count, len(combos))):
        user, movie, comment_type = combos[index]
        comment_time = ensure_after_anchor(event_datetime(batch_date, 2, index), user.get("_register_time"))
        content = (
            f"Seed short comment {index + 1} for movie {movie['movie_id']}"
            if comment_type == 1
            else json.dumps(
                {
                    "type": "doc",
                    "content": [
                        {
                            "type": "paragraph",
                            "content": [{"type": "text", "text": f"Seed long comment {index + 1} for {movie['name']}"}],
                        }
                    ],
                },
                separators=(",", ":"),
            )
        )
        rows.append(
            {
                "comment_id": stable_bigint(f"{batch_tag}:comment:{index}"),
                "user_id": user["user_id"],
                "movie_id": movie["movie_id"],
                "content": content,
                "votes": 2 + index,
                "comment_time": comment_time,
                "title": None if comment_type == 1 else f"Seed Review {index + 1}",
                "type": comment_type,
                "version": 0,
                "status": 2,
            }
        )
    return rows


def build_rating_rows(
    batch_tag: str,
    batch_date: dt.date,
    pairs: list[tuple[dict[str, Any], dict[str, Any]]],
    entity_count: int,
    args: argparse.Namespace,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for index in range(min(entity_count, len(pairs))):
        user, movie = pairs[index]
        rows.append(
            {
                "user_id": user["user_id"],
                "movie_id": movie["movie_id"],
                "rating": deterministic_rating(f"{batch_tag}:rating:{user['user_id']}:{movie['movie_id']}:{index}", args.rating_bias),
                "rating_time": ensure_after_anchor(event_datetime(batch_date, 3, index), user.get("_register_time")),
            }
        )
    return rows


def build_favorite_rows(
    batch_date: dt.date,
    pairs: list[tuple[dict[str, Any], dict[str, Any]]],
    folder_by_user_id: dict[str, dict[str, Any]],
    entity_count: int,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for index in range(min(entity_count, len(pairs))):
        user, movie = pairs[index]
        folder = folder_by_user_id[user["user_id"]]
        rows.append(
            {
                "user_id": user["user_id"],
                "movie_id": movie["movie_id"],
                "folder_id": folder["id"],
                "create_time": ensure_after_anchor(event_datetime(batch_date, 4, index), user.get("_register_time")),
            }
        )
    return rows


def build_view_history_rows(
    batch_tag: str,
    batch_date: dt.date,
    pairs: list[tuple[dict[str, Any], dict[str, Any]]],
    entity_count: int,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for index in range(min(entity_count, len(pairs))):
        user, movie = pairs[index]
        rows.append(
            {
                "history_id": stable_bigint(f"{batch_tag}:view_history:{index}"),
                "user_id": user["user_id"],
                "movie_id": movie["movie_id"],
                "view_time": ensure_after_anchor(
                    event_datetime(batch_date, 5, index),
                    user.get("_register_time"),
                ),
            }
        )
    return rows


def build_watched_rows(
    batch_date: dt.date,
    pairs: list[tuple[dict[str, Any], dict[str, Any]]],
    entity_count: int,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for index in range(min(entity_count, len(pairs))):
        user, movie = pairs[index]
        rows.append(
            {
                "user_id": user["user_id"],
                "movie_id": movie["movie_id"],
                "create_time": ensure_after_anchor(event_datetime(batch_date, 6, index), user.get("_register_time")),
            }
        )
    return rows


def build_comment_like_rows(
    batch_tag: str,
    batch_date: dt.date,
    comments: list[dict[str, Any]],
    users: list[dict[str, Any]],
    entity_count: int,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for index in range(min(entity_count, len(comments))):
        comment = comments[index]
        owner_user_id = comment["user_id"]
        liker = users[(index + 1) % len(users)] if len(users) > 1 else users[0]
        if liker["user_id"] == owner_user_id and len(users) > 1:
            liker = users[(index + 2) % len(users)]
        rows.append(
            {
                "id": stable_bigint(f"{batch_tag}:comment_like:{index}"),
                "comment_id": comment["comment_id"],
                "user_id": liker["user_id"],
                "create_time": ensure_after_anchor(event_datetime(batch_date, 7, index), liker.get("_register_time")),
            }
        )
    return rows


def update_folder_movie_counts(folders: list[dict[str, Any]], favorites: list[dict[str, Any]]) -> None:
    counts: dict[int, int] = {}
    for favorite in favorites:
        counts[favorite["folder_id"]] = counts.get(favorite["folder_id"], 0) + 1
    for folder in folders:
        folder["movie_count"] = counts.get(folder["id"], 0)


def build_event_envelope(batch_tag: str, event_type: str, event_index: int, key: str, occurred_at: dt.datetime, data: dict[str, Any]) -> dict[str, Any]:
    return {
        "eventId": seeded_uuid(batch_tag, event_type, event_index, key),
        "eventType": event_type,
        "occurredAt": int(occurred_at.timestamp() * 1000),
        "data": data,
    }


def shrink_user_cohort(users: list[dict[str, Any]], ratio: float) -> list[dict[str, Any]]:
    if len(users) <= 1:
        return list(users)
    target_size = max(1, int(len(users) * ratio))
    if target_size >= len(users):
        target_size = len(users) - 1
    return users[:target_size]


def determine_funnel_path(user_id: str, batch_tag: str) -> dict[str, bool]:
    """
    Deterministically determine which funnel steps a user completes.
    Returns a dict with keys: has_view, has_rating, has_comment, has_favorite, has_watched.
    Target conversion rates:
    - view -> rating: 60%
    - rating -> comment: 50%
    - comment -> favorite: 40%
    - favorite -> watched: 30%
    """
    # Generate deterministic hash values for each step
    base_seed = f"{batch_tag}:{user_id}"
    # Use different seeds for each step to get independent decisions
    view_hash = stable_bigint(f"{base_seed}:view") % 100
    rating_hash = stable_bigint(f"{base_seed}:rating") % 100
    comment_hash = stable_bigint(f"{base_seed}:comment") % 100
    favorite_hash = stable_bigint(f"{base_seed}:favorite") % 100
    watched_hash = stable_bigint(f"{base_seed}:watched") % 100

    # 95% of users have view (view_history event)
    has_view = view_hash < 95

    # 60% of viewers proceed to rating
    has_rating = has_view and (rating_hash < 60)

    # 50% of raters proceed to comment
    has_comment = has_rating and (comment_hash < 50)

    # 40% of commenters proceed to favorite (ADD operation)
    has_favorite = has_comment and (favorite_hash < 40)

    # 30% of favoriters proceed to watched
    has_watched = has_favorite and (watched_hash < 30)

    return {
        "has_view": has_view,
        "has_rating": has_rating,
        "has_comment": has_comment,
        "has_favorite": has_favorite,
        "has_watched": has_watched,
    }


def filter_rows_by_user_ids(rows: list[dict[str, Any]], user_ids: set[str]) -> list[dict[str, Any]]:
    if not rows or not user_ids:
        return rows
    filtered_rows = [row for row in rows if row.get("user_id") in user_ids]
    return filtered_rows or rows


def build_kafka_records(
    batch_tag: str,
    batch_date: dt.date,
    events_per_type: int,
    existing_users: list[dict[str, Any]],
    registered_users: list[dict[str, Any]],
    movies: list[dict[str, Any]],
    registered_folders: list[dict[str, Any]],
    comments: list[dict[str, Any]],
    ratings: list[dict[str, Any]],
    favorites: list[dict[str, Any]],
    view_history_rows: list[dict[str, Any]],
    watched_rows: list[dict[str, Any]],
    comment_likes: list[dict[str, Any]],
    args: argparse.Namespace,
) -> list[KafkaRecord]:
    kafka_records: list[KafkaRecord] = []
    active_registered_users = registered_users[: min(len(registered_users), max(1, min(events_per_type, args.display_registered_user_cap)))]
    # Determine funnel path for each active user
    user_funnel_paths = {}
    for user in active_registered_users:
        user_funnel_paths[user["user_id"]] = determine_funnel_path(user["user_id"], batch_tag)
    search_users = active_registered_users
    view_users = shrink_user_cohort(search_users, 0.8)
    rating_users = shrink_user_cohort(view_users, 0.75)
    comment_users = shrink_user_cohort(rating_users, 0.85)
    favorite_users = shrink_user_cohort(comment_users, 0.8)
    watched_users = shrink_user_cohort(favorite_users, 0.75)
    folder_action_user_count = min(
        len(search_users),
        max(len(comment_users), len(favorite_users) + 1),
    )
    folder_action_users = search_users[:folder_action_user_count] or search_users
    login_users = existing_users[: min(len(existing_users), args.extra_login_user_cap)] + search_users

    view_user_ids = {user["user_id"] for user in view_users}
    rating_user_ids = {uid for uid in {user["user_id"] for user in rating_users} if user_funnel_paths.get(uid, {}).get("has_rating", False)}
    comment_user_ids = {uid for uid in {user["user_id"] for user in comment_users} if user_funnel_paths.get(uid, {}).get("has_comment", False)}
    favorite_user_ids = {uid for uid in {user["user_id"] for user in favorite_users} if user_funnel_paths.get(uid, {}).get("has_favorite", False)}
    watched_user_ids = {uid for uid in {user["user_id"] for user in watched_users} if user_funnel_paths.get(uid, {}).get("has_watched", False)}
    search_user_ids = {user["user_id"] for user in search_users}
    folder_action_user_ids = {user["user_id"] for user in folder_action_users}

    stage_view_rows = filter_rows_by_user_ids(view_history_rows, view_user_ids)
    stage_rating_rows = filter_rows_by_user_ids(ratings, rating_user_ids)
    stage_comment_rows = filter_rows_by_user_ids(comments, comment_user_ids)
    stage_comment_like_rows = filter_rows_by_user_ids(comment_likes, search_user_ids)
    stage_favorite_rows = filter_rows_by_user_ids(favorites, favorite_user_ids)
    stage_watched_rows = filter_rows_by_user_ids(watched_rows, watched_user_ids)
    stage_folder_rows = filter_rows_by_user_ids(registered_folders, folder_action_user_ids)

    for event_type in EVENT_ORDER:
        for item_index in range(events_per_type):
            occurred_at = None
            if event_type == "view_history":
                source_row = pick_cycle(stage_view_rows, item_index)
                user_id = source_row["user_id"]
                user_path = user_funnel_paths.get(user_id, {})
                has_view = user_path.get("has_view", True)  # has_view always True
                if not has_view:
                    # Skip this event if user shouldn't have view (edge case)
                    continue
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                key = str(source_row["movie_id"])
                payload = {
                    "userId": user_id,
                    "movieId": source_row["movie_id"],
                    "viewTime": int(source_row["view_time"].timestamp() * 1000),
                }
            elif event_type == "rating":
                source_row = pick_cycle(stage_rating_rows, item_index)
                user_id = source_row["user_id"]
                user_path = user_funnel_paths.get(user_id, {})
                has_rating = user_path.get("has_rating", False)
                if not has_rating:
                    # Skip rating event if user shouldn't have rating
                    continue
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                operation = RATING_OPERATIONS[item_index % len(RATING_OPERATIONS)]
                key = str(source_row["movie_id"])
                payload = {
                    "userId": user_id,
                    "movieId": source_row["movie_id"],
                    "rating": source_row["rating"] if operation in {"CREATE", "UPDATE"} else None,
                    "operation": operation,
                    "ratingTime": occurred_at.isoformat(),
                }
            elif event_type == "comment":
                source_row = pick_cycle(stage_comment_rows, item_index)
                user_id = source_row["user_id"]
                user_path = user_funnel_paths.get(user_id, {})
                has_comment = user_path.get("has_comment", False)
                if not has_comment:
                    # Skip comment event if user shouldn't have comment
                    continue
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                operation = COMMENT_OPERATIONS[item_index % len(COMMENT_OPERATIONS)]
                key = str(source_row["movie_id"])
                payload = {
                    "userId": user_id,
                    "movieId": source_row["movie_id"],
                    "commentId": source_row["comment_id"],
                    "type": source_row["type"],
                    "operation": operation,
                    "contentLength": len(source_row["content"]),
                }
            elif event_type == "comment_like":
                source_row = pick_cycle(stage_comment_like_rows, item_index)
                user_id = source_row["user_id"]
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                key = str(source_row["comment_id"])
                payload = {
                    "userId": user_id,
                    "commentId": source_row["comment_id"],
                    "operation": COMMENT_LIKE_OPERATIONS[item_index % len(COMMENT_LIKE_OPERATIONS)],
                }
            elif event_type == "favorite":
                source_row = pick_cycle(stage_favorite_rows, item_index)
                user_id = source_row["user_id"]
                user_path = user_funnel_paths.get(user_id, {})
                has_favorite = user_path.get("has_favorite", False)
                if not has_favorite:
                    operation = "REMOVE"
                elif item_index < len(favorite_users):
                    operation = "ADD"
                else:
                    # Deterministic operation selection: ~80% ADD, 20% REMOVE
                    hash_val = stable_bigint(f"{batch_tag}:favorite:{item_index}") % 10
                    operation = "ADD" if hash_val < 8 else "REMOVE"
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                key = str(source_row["movie_id"])
                payload = {
                    "userId": user_id,
                    "movieId": source_row["movie_id"],
                    "folderId": source_row["folder_id"],
                    "operation": operation,
                }
            elif event_type == "watched":
                source_row = pick_cycle(stage_watched_rows, item_index)
                user_id = source_row["user_id"]
                user_path = user_funnel_paths.get(user_id, {})
                has_watched = user_path.get("has_watched", False)
                if not has_watched:
                    # Skip watched event if user shouldn't have watched
                    continue
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                key = str(source_row["movie_id"])
                payload = {
                    "userId": user_id,
                    "movieId": source_row["movie_id"],
                    "watchedTime": int(source_row["create_time"].timestamp() * 1000),
                }
            elif event_type == "favorite_folder_action":
                source_row = pick_cycle(stage_folder_rows, item_index)
                user_id = source_row["user_id"]
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                operation = FOLDER_OPERATIONS[item_index % len(FOLDER_OPERATIONS)]
                folder_name = source_row["name"] if operation != "UPDATE" else f"{source_row['name']}_updated"
                is_public = source_row["is_public"] if operation != "SHARE" else 1
                key = str(source_row["id"])
                payload = {
                    "userId": user_id,
                    "folderId": source_row["id"],
                    "folderName": folder_name,
                    "isPublic": is_public,
                    "operation": operation,
                }
            elif event_type == "search":
                # Search events should belong to users who can also produce later
                # view/rating/favorite/watched actions on the same day, so the
                # downstream search funnel ADS can show real conversions.
                user = pick_cycle(search_users, item_index)
                user_id = user["user_id"]
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                movie = pick_cycle(movies, item_index)
                keyword = movie["name"]
                key = keyword
                payload = {
                    "userId": user_id,
                    "searchKeyword": keyword,
                    "filterConditions": {
                        "genres": [movie["genres"].split("/")[0]],
                        "yearFrom": movie["year"] - 1,
                        "yearTo": movie["year"] + 1,
                        "regions": [movie["regions"]],
                    },
                    "resultCount": 0 if stable_bigint(f"{batch_tag}:search:{item_index}") % 10 >= 7 else 5 + (stable_bigint(f"{batch_tag}:search:{item_index}") % 46),
                    "searchTime": 80 + item_index * 11,
                }
            elif event_type == "user_register":
                user = pick_cycle(search_users, item_index)
                user_id = user["user_id"]
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                key = user_id
                payload = {"userId": user_id}
            elif event_type == "user_login":
                user = pick_cycle(login_users, item_index)
                user_id = user["user_id"]
                occurred_at = user_event_datetime(batch_date, user_id, event_type, item_index, batch_tag)
                key = user_id
                payload = {"userId": user_id}
            else:
                raise ValueError(f"Unsupported event type: {event_type}")

            envelope = build_event_envelope(batch_tag, event_type, item_index, key, occurred_at, payload)
            kafka_records.append(
                KafkaRecord(
                    topic=TOPIC_BY_EVENT_TYPE[event_type],
                    key=key,
                    value=json.dumps(envelope, ensure_ascii=False, separators=(",", ":")),
                    event_type=event_type,
                )
            )

    return kafka_records


def build_table_datasets(
    users: list[dict[str, Any]],
    folders: list[dict[str, Any]],
    comments: list[dict[str, Any]],
    ratings: list[dict[str, Any]],
    favorites: list[dict[str, Any]],
    view_history_rows: list[dict[str, Any]],
    watched_rows: list[dict[str, Any]],
    comment_likes: list[dict[str, Any]],
    args: argparse.Namespace,
) -> list[TableDataset]:
    rows_by_table = {
        "public.users": users,
        "public.favorite_folders": folders,
        "public.comments": comments,
        "public.ratings": ratings,
        "public.favorites": favorites,
        "public.view_history": view_history_rows,
        "public.watched_movies": watched_rows,
        "public.comment_likes": comment_likes,
    }
    cleanup_by_table = {
        "public.comment_likes": build_delete_in_statements(
            "public.comment_likes", "id", [row["id"] for row in comment_likes]
        ),
        "public.favorites": build_delete_in_statements(
            "public.favorites", "user_id", [row["user_id"] for row in favorites], type_hint="text"
        ),
        "public.watched_movies": build_delete_in_statements(
            "public.watched_movies", "user_id", [row["user_id"] for row in watched_rows], type_hint="text"
        ),
        "public.view_history": build_delete_in_statements(
            "public.view_history", "history_id", [row["history_id"] for row in view_history_rows]
        ),
        "public.ratings": build_delete_in_statements(
            "public.ratings", "user_id", [row["user_id"] for row in ratings], type_hint="text"
        ),
        "public.comments": build_delete_in_statements(
            "public.comments", "comment_id", [row["comment_id"] for row in comments]
        ),
        "public.favorite_folders": build_delete_in_statements(
            "public.favorite_folders", "id", [row["id"] for row in folders]
        ),
        "public.users": build_delete_in_statements(
            "public.users", "user_id", [row["user_id"] for row in users], type_hint="text"
        ),
    }

    table_datasets: list[TableDataset] = []
    for table_name in TABLE_ORDER:
        table_datasets.append(
            TableDataset(
                table_name=table_name,
                rows=rows_by_table[table_name],
                cleanup_statements=cleanup_by_table.get(table_name, []),
                insert_statements=build_insert_statements(table_name, rows_by_table[table_name], args),
                post_statements=[SEQUENCE_RESET_SQL[table_name]] if table_name in SEQUENCE_RESET_SQL else [],
            )
        )
    return table_datasets


def generate_dataset(
    batch_date: dt.date,
    batch_tag: str,
    existing_users: list[dict[str, Any]],
    movies: list[dict[str, Any]],
    events_per_type: int,
    args: argparse.Namespace,
) -> GeneratedDataset:
    registered_user_count = min(max(1, events_per_type), args.display_registered_user_cap)
    registered_users = build_registered_user_rows(batch_tag, batch_date, registered_user_count)
    folders = build_folder_rows(batch_tag, batch_date, registered_users)
    folder_by_user_id = {folder["user_id"]: folder for folder in folders}
    registered_pairs = build_user_movie_pairs(registered_users, movies)

    entity_count = min(max(1, events_per_type), len(registered_pairs))
    comments = build_comment_rows(batch_tag, batch_date, registered_users, movies, max(1, events_per_type))
    ratings = build_rating_rows(batch_tag, batch_date, registered_pairs, entity_count, args)
    favorites = build_favorite_rows(batch_date, registered_pairs, folder_by_user_id, entity_count)
    view_history_rows = build_view_history_rows(batch_tag, batch_date, registered_pairs, max(1, events_per_type))
    watched_rows = build_watched_rows(batch_date, registered_pairs, entity_count)
    comment_likes = build_comment_like_rows(
        batch_tag,
        batch_date,
        comments,
        registered_users,
        max(1, events_per_type),
    )
    update_folder_movie_counts(folders, favorites)
    registered_folders = [folder_by_user_id[user["user_id"]] for user in registered_users]

    kafka_records = build_kafka_records(
        batch_tag=batch_tag,
        batch_date=batch_date,
        events_per_type=events_per_type,
        existing_users=existing_users,
        registered_users=registered_users,
        movies=movies,
        registered_folders=registered_folders,
        comments=comments,
        ratings=ratings,
        favorites=favorites,
        view_history_rows=view_history_rows,
        watched_rows=watched_rows,
        comment_likes=comment_likes,
        args=args,
    )
    return GeneratedDataset(
        batch_date=batch_date.isoformat(),
        batch_tag=batch_tag,
        pg_tables=build_table_datasets(
            users=registered_users,
            folders=folders,
            comments=comments,
            ratings=ratings,
            favorites=favorites,
            view_history_rows=view_history_rows,
            watched_rows=watched_rows,
            comment_likes=comment_likes,
            args=args,
        ),
        kafka_records=kafka_records,
    )


def validate_required_topics(kafka_config: dict[str, Any]) -> None:
    configured_topics = {str(item) for item in kafka_config.get("topics", [])}
    missing_topics = [topic for topic in TOPIC_BY_EVENT_TYPE.values() if topic not in configured_topics]
    if missing_topics:
        joined_topics = ", ".join(sorted(missing_topics))
        raise ValueError(
            f"Configured kafka.topics is missing required topics for source generation: {joined_topics}"
        )


def execute_postgres_statements(spark: SparkSession, pg_config: dict[str, Any], statements: list[str]) -> None:
    if not statements:
        return

    jvm = spark.sparkContext._gateway.jvm
    connection = None
    statement = None
    try:
        jvm.java.lang.Class.forName(pg_config.get("driver", "org.postgresql.Driver"))
        connection = jvm.java.sql.DriverManager.getConnection(
            pg_config["jdbc_url"], pg_config["user"], pg_config["password"]
        )
        connection.setAutoCommit(False)
        statement = connection.createStatement()
        for sql in statements:
            statement.execute(sql)
        connection.commit()
    except Exception:
        if connection is not None:
            connection.rollback()
        raise
    finally:
        if statement is not None:
            statement.close()
        if connection is not None:
            connection.close()


def publish_kafka_records(spark: SparkSession, bootstrap_servers: str, records: list[KafkaRecord]) -> None:
    if not records:
        return

    dataframe = spark.createDataFrame([(record.topic, record.key, record.value) for record in records], ["topic", "key", "value"])
    dataframe.write.format("kafka").option("kafka.bootstrap.servers", bootstrap_servers).save()


def export_fixture_files(dataset: GeneratedDataset, fixture_dir: str) -> Path:
    batch_root = Path(fixture_dir) / dataset.batch_tag
    postgres_root = batch_root / "postgres"
    kafka_root = batch_root / "kafka"
    postgres_root.mkdir(parents=True, exist_ok=True)
    kafka_root.mkdir(parents=True, exist_ok=True)

    for table in dataset.pg_tables:
        file_path = postgres_root / f"{table.table_name.split('.')[-1]}.sql"
        lines = ["-- Auto-generated fixture for source data generation", f"-- batch_tag={dataset.batch_tag}", ""]
        lines.extend(table.cleanup_statements)
        if table.cleanup_statements:
            lines.append("")
        lines.extend(table.insert_statements)
        if table.insert_statements and table.post_statements:
            lines.append("")
        lines.extend(table.post_statements)
        file_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")

    records_by_topic: dict[str, list[str]] = {}
    for record in dataset.kafka_records:
        records_by_topic.setdefault(record.topic, []).append(record.value)
    for topic, payloads in records_by_topic.items():
        file_path = kafka_root / f"{topic}.jsonl"
        file_path.write_text("\n".join(payloads) + "\n", encoding="utf-8")

    return batch_root


def compute_statistics(dataset: GeneratedDataset) -> dict[str, Any]:
    """Compute various statistics about the generated dataset."""
    stats: dict[str, Any] = {}

    # Table row counts
    table_counts: dict[str, int] = {}
    for table in dataset.pg_tables:
        table_name = table.table_name.split(".")[-1]  # Remove schema
        table_counts[table_name] = len(table.rows)
    stats["table_counts"] = table_counts

    # Rating statistics
    rating_values: list[float] = []
    for table in dataset.pg_tables:
        if table.table_name == "public.ratings":
            for row in table.rows:
                rating = row.get("rating")
                if rating is not None:
                    rating_values.append(rating)
    if rating_values:
        stats["rating_count"] = len(rating_values)
        stats["rating_avg"] = sum(rating_values) / len(rating_values)
        stats["rating_min"] = min(rating_values)
        stats["rating_max"] = max(rating_values)
        # Distribution (count per integer value)
        dist: dict[int, int] = {}
        for r in rating_values:
            key = int(round(r))
            dist[key] = dist.get(key, 0) + 1
        stats["rating_distribution"] = dist

    # Event time range (from Kafka records)
    timestamps: list[dt.datetime] = []
    for record in dataset.kafka_records:
        try:
            data = json.loads(record.value)
            event_time_str = data.get("data", {}).get("eventTime")
            if event_time_str:
                # Parse ISO format datetime
                dt_obj = dt.datetime.fromisoformat(event_time_str.replace("Z", "+00:00"))
                timestamps.append(dt_obj)
        except (json.JSONDecodeError, ValueError, AttributeError):
            pass
    if timestamps:
        stats["event_time_min"] = min(timestamps)
        stats["event_time_max"] = max(timestamps)
        stats["event_time_range_hours"] = (max(timestamps) - min(timestamps)).total_seconds() / 3600

    # User activity (count events per user from Kafka)
    user_activity: dict[str, int] = {}
    for record in dataset.kafka_records:
        try:
            data = json.loads(record.value)
            user_id = data.get("data", {}).get("userId")
            if user_id:
                user_activity[str(user_id)] = user_activity.get(str(user_id), 0) + 1
        except (json.JSONDecodeError, ValueError, AttributeError):
            pass
    if user_activity:
        stats["user_count"] = len(user_activity)
        stats["user_activity_avg"] = sum(user_activity.values()) / len(user_activity)
        stats["user_activity_min"] = min(user_activity.values())
        stats["user_activity_max"] = max(user_activity.values())

    return stats


def print_summary(dataset: GeneratedDataset, write_mode: str, fixture_root: Path | None) -> None:
    event_type_counts: dict[str, int] = {}
    for record in dataset.kafka_records:
        event_type_counts[record.event_type] = event_type_counts.get(record.event_type, 0) + 1

    print(
        "Generated DWD source data "
        f"batch_date={dataset.batch_date} batch_tag={dataset.batch_tag} "
        f"write_mode={write_mode} pg_tables={len(dataset.pg_tables)} kafka_records={len(dataset.kafka_records)}"
    )
    print("Kafka event counts:")
    for event_type in EVENT_ORDER:
        print(f"  - {event_type}: {event_type_counts.get(event_type, 0)}")
    if fixture_root is not None:
        print(f"Fixture output: {fixture_root}")

    # Compute and print additional statistics
    stats = compute_statistics(dataset)

    print("\n--- Dataset Statistics ---")

    # Table row counts
    if "table_counts" in stats:
        print("Table row counts:")
        for table_name, count in sorted(stats["table_counts"].items()):
            print(f"  - {table_name}: {count}")

    # Rating statistics
    if "rating_count" in stats:
        print(f"Ratings: {stats['rating_count']} total, avg {stats['rating_avg']:.2f}, min {stats['rating_min']:.1f}, max {stats['rating_max']:.1f}")
        if "rating_distribution" in stats:
            dist_str = ", ".join(f"{star}★: {count}" for star, count in sorted(stats["rating_distribution"].items()))
            print(f"  Distribution: {dist_str}")

    # Event time range
    if "event_time_min" in stats and "event_time_max" in stats:
        min_time = stats["event_time_min"].strftime("%Y-%m-%d %H:%M:%S")
        max_time = stats["event_time_max"].strftime("%Y-%m-%d %H:%M:%S")
        range_hours = stats.get("event_time_range_hours", 0)
        print(f"Event time range: {min_time} to {max_time} ({range_hours:.1f} hours)")

    # User activity
    if "user_count" in stats:
        print(f"Active users: {stats['user_count']}, avg {stats['user_activity_avg']:.1f} events/user, range [{stats['user_activity_min']}-{stats['user_activity_max']}]")


def validate_dataset(dataset: GeneratedDataset) -> list[str]:
    """Validate dataset integrity and return list of warnings/errors."""
    issues: list[str] = []

    # 1. Check rating values are in valid range
    for table in dataset.pg_tables:
        if table.table_name == "public.ratings":
            for i, row in enumerate(table.rows):
                rating = row.get("rating")
                if rating is None:
                    issues.append(f"Rating row {i} has null rating")
                elif not (1.0 <= rating <= 5.0):
                    issues.append(f"Rating row {i} has invalid rating {rating}")

    # 2. Check timestamps are not null for timestamp columns
    timestamp_tables = ["public.users", "public.favorite_folders", "public.comments",
                       "public.ratings", "public.favorites", "public.view_history",
                       "public.watched_movies", "public.comment_likes"]
    for table in dataset.pg_tables:
        if table.table_name in timestamp_tables:
            for i, row in enumerate(table.rows):
                # Check timestamp columns based on table
                if table.table_name == "public.users":
                    if row.get("create_time") is None:
                        issues.append(f"User {row.get('user_id')} has null create_time")
                elif table.table_name == "public.comments":
                    if row.get("comment_time") is None:
                        issues.append(f"Comment {row.get('comment_id')} has null comment_time")
                elif table.table_name == "public.ratings":
                    if row.get("rating_time") is None:
                        issues.append(f"Rating for user {row.get('user_id')} has null rating_time")
                # Add more checks as needed

    # 3. Check Kafka records have required fields
    for i, record in enumerate(dataset.kafka_records):
        try:
            value = json.loads(record.value)
            if "eventId" not in value:
                issues.append(f"Kafka record {i} missing eventId")
            if "data" not in value:
                issues.append(f"Kafka record {i} missing data")
        except json.JSONDecodeError:
            issues.append(f"Kafka record {i} has invalid JSON")

    return issues


def run() -> None:
    args = parse_args()
    validate_positive_int("user_count", args.user_count)
    validate_positive_int("movie_count", args.movie_count)
    validate_positive_int("events_per_type", args.events_per_type)
    validate_rating_bias(args.rating_bias)
    if args.spark_parallelism is not None:
        validate_positive_int("spark_parallelism", args.spark_parallelism)

    config = load_config(args.config)
    spark_config: dict[str, Any] = config["spark"]
    pg_config: dict[str, Any] = config["postgres"]
    # Apply Spark parallelism if specified
    if args.spark_parallelism is not None:
        spark_config["spark.sql.shuffle.partitions"] = args.spark_parallelism
        spark_config["spark.default.parallelism"] = args.spark_parallelism
    batch_date = dt.date.fromisoformat(args.batch_date)
    batch_tag = normalize_batch_tag(args.batch_tag or f"dwdsrc_{batch_date.strftime('%Y%m%d')}", args.max_identifier_tag_len)
    spark = build_spark_session("movie-generate-dwd-user-event-source-data", spark_config)
    try:
        existing_users = load_existing_users(spark, pg_config, args.user_count)
        movies = load_existing_movies(spark, pg_config, args.movie_count, batch_date)
        dataset = generate_dataset(
            batch_date=batch_date,
            batch_tag=batch_tag,
            existing_users=existing_users,
            movies=movies,
            events_per_type=args.events_per_type,
            args=args,
        )

        # Validate dataset integrity
        if args.validation_mode != "none":
            issues = validate_dataset(dataset)
            if issues:
                if args.validation_mode == "error":
                    raise ValueError(f"Data validation failed with {len(issues)} issue(s):\n" + "\n".join(f"  - {issue}" for issue in issues))
                else:  # warn
                    print(f"WARNING: Data validation found {len(issues)} issue(s):")
                    for issue in issues:
                        print(f"  - {issue}")

        fixture_root: Path | None = None
        if args.write_mode in {"fixtures", "both"}:
            fixture_root = export_fixture_files(dataset, args.fixture_dir)

        if args.write_mode in {"direct", "both"}:
            kafka_config: dict[str, Any] = config["kafka"]
            validate_required_topics(kafka_config)

            postgres_statements: list[str] = []
            for table in reversed(dataset.pg_tables):
                postgres_statements.extend(table.cleanup_statements)
            for table in dataset.pg_tables:
                postgres_statements.extend(table.insert_statements)
                postgres_statements.extend(table.post_statements)

            execute_postgres_statements(spark, pg_config, postgres_statements)
            publish_kafka_records(spark, kafka_config["bootstrap_servers"], dataset.kafka_records)

        print_summary(dataset, args.write_mode, fixture_root)
    finally:
        spark.stop()


if __name__ == "__main__":
    run()
