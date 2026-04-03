#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash run_generate_dwd_user_event_source_data.sh [batch-date] [config-path]
  bash run_generate_dwd_user_event_source_data.sh --batch-date YYYY-MM-DD --config conf/etl_config.json

Examples:
  bash run_generate_dwd_user_event_source_data.sh
  bash run_generate_dwd_user_event_source_data.sh 2026-03-31
  bash run_generate_dwd_user_event_source_data.sh 2026-03-31 conf/etl_config.json
  bash run_generate_dwd_user_event_source_data.sh --batch-date 2026-03-31 --write-mode direct --events-per-type 3
  bash run_generate_dwd_user_event_source_data.sh --write-mode fixtures --fixture-dir fixtures/dwd_seed

Arguments:
  batch-date       Business date, default: today (YYYY-MM-DD)
  config-path      Config file path, default: conf/etl_config.json
  user-count       Existing PostgreSQL user sample count, default: 4
  movie-count      Existing PostgreSQL movie sample count, default: 6
  events-per-type  Kafka event count per event type, default: 2
  write-mode       direct, fixtures, both; default: both
  fixture-dir      Fixture output directory
  batch-tag        Optional deterministic tag
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

BATCH_DATE="$(date +%F)"
CONFIG_PATH="conf/etl_config.json"
USER_COUNT="4"
MOVIE_COUNT="6"
EVENTS_PER_TYPE="2"
WRITE_MODE="both"
FIXTURE_DIR="fixtures/dwd_user_event_source_data"
BATCH_TAG=""
POSITIONAL_ARGS=()

is_date() {
  [[ "$1" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --batch-date)
      BATCH_DATE="${2:-}"
      shift 2
      ;;
    --config)
      CONFIG_PATH="${2:-}"
      shift 2
      ;;
    --user-count)
      USER_COUNT="${2:-}"
      shift 2
      ;;
    --movie-count)
      MOVIE_COUNT="${2:-}"
      shift 2
      ;;
    --events-per-type)
      EVENTS_PER_TYPE="${2:-}"
      shift 2
      ;;
    --write-mode)
      WRITE_MODE="${2:-}"
      shift 2
      ;;
    --fixture-dir)
      FIXTURE_DIR="${2:-}"
      shift 2
      ;;
    --batch-tag)
      BATCH_TAG="${2:-}"
      shift 2
      ;;
    *)
      POSITIONAL_ARGS+=("$1")
      shift
      ;;
  esac
done

case "${#POSITIONAL_ARGS[@]}" in
  0)
    ;;
  1)
    if is_date "${POSITIONAL_ARGS[0]}"; then
      BATCH_DATE="${POSITIONAL_ARGS[0]}"
    else
      CONFIG_PATH="${POSITIONAL_ARGS[0]}"
    fi
    ;;
  2)
    BATCH_DATE="${POSITIONAL_ARGS[0]}"
    CONFIG_PATH="${POSITIONAL_ARGS[1]}"
    ;;
  *)
    usage
    exit 1
    ;;
esac

if ! command -v spark-submit >/dev/null 2>&1; then
  echo "spark-submit not found in PATH" >&2
  exit 1
fi

if [[ ! -f "${CONFIG_PATH}" ]]; then
  echo "Config file not found: ${CONFIG_PATH}" >&2
  exit 1
fi

if [[ "${WRITE_MODE}" != "direct" && "${WRITE_MODE}" != "fixtures" && "${WRITE_MODE}" != "both" ]]; then
  echo "Invalid write mode: ${WRITE_MODE}" >&2
  echo "Expected: direct, fixtures, both" >&2
  exit 1
fi

CMD=(
  spark-submit
  --master yarn
  --deploy-mode client
  --packages org.postgresql:postgresql:42.7.3
  jobs/generate_dwd_user_event_source_data.py
  --config "${CONFIG_PATH}"
  --batch-date "${BATCH_DATE}"
  --user-count "${USER_COUNT}"
  --movie-count "${MOVIE_COUNT}"
  --events-per-type "${EVENTS_PER_TYPE}"
  --write-mode "${WRITE_MODE}"
  --fixture-dir "${FIXTURE_DIR}"
)

if [[ "${WRITE_MODE}" != "fixtures" ]]; then
  CMD=(
    spark-submit
    --master yarn
    --deploy-mode client
    --packages org.postgresql:postgresql:42.7.3,org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.2
    jobs/generate_dwd_user_event_source_data.py
    --config "${CONFIG_PATH}"
    --batch-date "${BATCH_DATE}"
    --user-count "${USER_COUNT}"
    --movie-count "${MOVIE_COUNT}"
    --events-per-type "${EVENTS_PER_TYPE}"
    --write-mode "${WRITE_MODE}"
    --fixture-dir "${FIXTURE_DIR}"
  )
fi

if [[ -n "${BATCH_TAG}" ]]; then
  CMD+=(--batch-tag "${BATCH_TAG}")
fi

printf 'Running command:\n%s\n' "${CMD[*]}"
"${CMD[@]}"
