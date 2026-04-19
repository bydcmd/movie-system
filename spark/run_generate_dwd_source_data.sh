#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash run_generate_dwd_source_data.sh [batch-date] [config-path] [generator-options]
  bash run_generate_dwd_source_data.sh [config-path] [generator-options]
  bash run_generate_dwd_source_data.sh --batch-date YYYY-MM-DD --config conf/etl_config.json [generator-options]

Description:
  Generate synthetic source data and append it directly into PostgreSQL business tables.

Examples:
  bash run_generate_dwd_source_data.sh
  bash run_generate_dwd_source_data.sh 2026-03-25
  bash run_generate_dwd_source_data.sh conf/etl_config.dev.json
  bash run_generate_dwd_source_data.sh 2026-03-25 conf/etl_config.json --user-target 200 --view-target 5000
  bash run_generate_dwd_source_data.sh --batch-date 2026-03-25 --config conf/etl_config.json --write-batch-size 1000

Arguments:
  batch-date   Logical data generation date, default: today (YYYY-MM-DD)
  config-path  Config file path, default: conf/etl_config.json

Generator options:
  --movie-limit N
  --user-target N
  --new-user-target N
  --folder-target N
  --view-target N
  --rating-target N
  --comment-target N
  --comment-like-target N
  --favorite-target N
  --watched-target N
  --lookback-days N
  --seed N
  --write-batch-size N
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

BATCH_DATE="$(date +%F)"
CONFIG_PATH="conf/etl_config.json"
POSITIONAL_ARGS=()
GENERATOR_ARGS=()

is_date() {
  [[ "$1" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]
}

append_option_with_value() {
  local option_name="$1"
  local option_value="${2:-}"
  if [[ -z "${option_value}" || "${option_value}" == --* ]]; then
    echo "Missing value for ${option_name}" >&2
    exit 1
  fi
  GENERATOR_ARGS+=("${option_name}" "${option_value}")
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
    --movie-limit|--user-target|--new-user-target|--folder-target|--view-target|--rating-target|--comment-target|--comment-like-target|--favorite-target|--watched-target|--lookback-days|--seed|--write-batch-size)
      append_option_with_value "$1" "${2:-}"
      shift 2
      ;;
    --)
      shift
      while [[ $# -gt 0 ]]; do
        GENERATOR_ARGS+=("$1")
        shift
      done
      ;;
    --*)
      GENERATOR_ARGS+=("$1")
      if [[ $# -gt 1 && "${2:-}" != --* ]]; then
        GENERATOR_ARGS+=("$2")
        shift 2
      else
        shift
      fi
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

if ! is_date "${BATCH_DATE}"; then
  echo "Invalid batch date: ${BATCH_DATE}" >&2
  exit 1
fi

if ! command -v spark-submit >/dev/null 2>&1; then
  echo "spark-submit not found in PATH" >&2
  exit 1
fi

if [[ ! -f "${CONFIG_PATH}" ]]; then
  echo "Config file not found: ${CONFIG_PATH}" >&2
  exit 1
fi

# Batch job memory configuration for 4c8g pseudo-distributed cluster
CMD=(
  spark-submit
  --master yarn
  --deploy-mode client
  --driver-memory 1g
  --executor-memory 2g
  --executor-cores 2
  --num-executors 1
  --conf spark.executor.memoryOverhead=512m
  --conf spark.sql.shuffle.partitions=8
  --conf spark.sql.adaptive.enabled=true
  --conf spark.sql.adaptive.coalescePartitions.enabled=true
  --conf spark.memory.fraction=0.5
  --conf spark.memory.storageFraction=0.3
  --conf spark.serializer=org.apache.spark.serializer.KryoSerializer
  --conf spark.driver.maxResultSize=256m
  --conf spark.network.timeout=600s
  --packages org.postgresql:postgresql:42.7.3
  jobs/generate_dwd_user_event_source_data.py
  --config "${CONFIG_PATH}"
  --batch-date "${BATCH_DATE}"
)

if [[ "${#GENERATOR_ARGS[@]}" -gt 0 ]]; then
  CMD+=("${GENERATOR_ARGS[@]}")
fi

printf 'Running command:\n%s\n' "${CMD[*]}"
"${CMD[@]}"
