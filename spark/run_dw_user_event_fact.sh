#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash run_dw_user_event_fact.sh [calc-date] [config-path] [snapshot-date]
  bash run_dw_user_event_fact.sh [config-path]
  bash run_dw_user_event_fact.sh --calc-date YYYY-MM-DD --config conf/etl_config.json --snapshot-date YYYY-MM-DD

Description:
  Build `dw.dw_user_event_fact_di` in offline T+1 batch mode from PostgreSQL source tables.

Examples:
  bash run_dw_user_event_fact.sh
  bash run_dw_user_event_fact.sh 2026-03-25
  bash run_dw_user_event_fact.sh conf/etl_config.dev.json
  bash run_dw_user_event_fact.sh 2026-03-25 conf/etl_config.dev.json
  bash run_dw_user_event_fact.sh 2026-03-25 conf/etl_config.dev.json 2026-03-24

Arguments:
  calc-date      Business date to build, default: today (YYYY-MM-DD)
  config-path    Config file path, default: conf/etl_config.json
  snapshot-date  Legacy Hive source partition date; ignored when dw_event_fact.source_mode=jdbc
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

CALC_DATE="$(date +%F)"
CONFIG_PATH="conf/etl_config.json"
SNAPSHOT_DATE=""
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
    --calc-date)
      CALC_DATE="${2:-}"
      shift 2
      ;;
    --config)
      CONFIG_PATH="${2:-}"
      shift 2
      ;;
    --snapshot-date)
      SNAPSHOT_DATE="${2:-}"
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
      CALC_DATE="${POSITIONAL_ARGS[0]}"
    else
      CONFIG_PATH="${POSITIONAL_ARGS[0]}"
    fi
    ;;
  2)
    CALC_DATE="${POSITIONAL_ARGS[0]}"
    CONFIG_PATH="${POSITIONAL_ARGS[1]}"
    ;;
  3)
    CALC_DATE="${POSITIONAL_ARGS[0]}"
    CONFIG_PATH="${POSITIONAL_ARGS[1]}"
    SNAPSHOT_DATE="${POSITIONAL_ARGS[2]}"
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

# Batch job memory configuration for 4c8g pseudo-distributed cluster
# Total memory budget: 8GB - (OS ~0.5G + HDFS/YARN ~2G) = ~5.5GB for Spark
# - Driver: 1g (minimal, only coordination)
# - Single executor: 2g with overhead (data processing)
# - shuffle.partitions: reduced for small cluster
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
  --conf spark.sql.adaptive.advisoryPartitionSizeInBytes=67108864
  --conf spark.memory.fraction=0.5
  --conf spark.memory.storageFraction=0.3
  --conf spark.serializer=org.apache.spark.serializer.KryoSerializer
  --conf spark.kryoserializer.buffer.max=128m
  --conf spark.driver.maxResultSize=256m
  --conf spark.network.timeout=600s
  --conf spark.executor.heartbeatInterval=60s
  --packages org.postgresql:postgresql:42.7.3
  jobs/build_dw_user_event_fact_di.py
  --config "${CONFIG_PATH}"
  --calc-date "${CALC_DATE}"
)

if [[ -n "${SNAPSHOT_DATE}" ]]; then
  CMD+=(--snapshot-date "${SNAPSHOT_DATE}")
fi

printf 'Running command:\n%s\n' "${CMD[*]}"
"${CMD[@]}"
