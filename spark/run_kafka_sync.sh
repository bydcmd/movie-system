#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash run_kafka_sync.sh [config-path] [run-mode]
  bash run_kafka_sync.sh --config conf/etl_config.json --run-mode streaming
  bash run_kafka_sync.sh --config conf/etl_config.json --run-mode available-now --checkpoint-root hdfs:///warehouse/checkpoints/movie-etl

Examples:
  bash run_kafka_sync.sh
  bash run_kafka_sync.sh conf/etl_config.dev.json
  bash run_kafka_sync.sh conf/etl_config.dev.json available-now
  bash run_kafka_sync.sh --config conf/etl_config.dev.json --run-mode streaming
  bash run_kafka_sync.sh --config conf/etl_config.dev.json --run-mode available-now --trigger-processing-time "30 seconds"

Arguments:
  config-path              Config file path, default: conf/etl_config.json
  run-mode                 streaming or available-now, default: streaming
  checkpoint-root          Optional checkpoint root override
  trigger-processing-time  Optional trigger interval override, e.g. 30 seconds
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

CONFIG_PATH="conf/etl_config.json"
RUN_MODE="streaming"
CHECKPOINT_ROOT=""
TRIGGER_PROCESSING_TIME=""
POSITIONAL_ARGS=()

is_run_mode() {
  [[ "$1" == "streaming" || "$1" == "available-now" ]]
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --config)
      CONFIG_PATH="${2:-}"
      shift 2
      ;;
    --run-mode)
      RUN_MODE="${2:-}"
      shift 2
      ;;
    --checkpoint-root)
      CHECKPOINT_ROOT="${2:-}"
      shift 2
      ;;
    --trigger-processing-time)
      TRIGGER_PROCESSING_TIME="${2:-}"
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
    if is_run_mode "${POSITIONAL_ARGS[0]}"; then
      RUN_MODE="${POSITIONAL_ARGS[0]}"
    else
      CONFIG_PATH="${POSITIONAL_ARGS[0]}"
    fi
    ;;
  2)
    CONFIG_PATH="${POSITIONAL_ARGS[0]}"
    RUN_MODE="${POSITIONAL_ARGS[1]}"
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

if ! is_run_mode "${RUN_MODE}"; then
  echo "Invalid run mode: ${RUN_MODE}" >&2
  echo "Expected: streaming or available-now" >&2
  exit 1
fi

# Streaming job memory configuration for 4c8g pseudo-distributed cluster
# Total memory budget: 8GB - (OS ~0.5G + HDFS/YARN ~2G) = ~5.5GB for Spark
# - Driver: 1g (minimal, only coordination)
# - Single executor: 2g with overhead (all data processing happens here)
# - shuffle.partitions: minimal for small batches
CMD=(
  spark-submit
  --master yarn
  --deploy-mode client
  --driver-memory 1g
  --executor-memory 2g
  --executor-cores 2
  --num-executors 1
  --conf spark.executor.memoryOverhead=512m
  --conf spark.sql.shuffle.partitions=4
  --conf spark.sql.adaptive.enabled=true
  --conf spark.sql.adaptive.coalescePartitions.enabled=true
  --conf spark.sql.adaptive.advisoryPartitionSizeInBytes=67108864
  --conf spark.memory.fraction=0.5
  --conf spark.memory.storageFraction=0.3
  --conf spark.streaming.backpressure.enabled=true
  --conf spark.streaming.kafka.maxRatePerPartition=500
  --conf spark.serializer=org.apache.spark.serializer.KryoSerializer
  --conf spark.kryoserializer.buffer.max=128m
  --conf spark.driver.maxResultSize=256m
  --conf spark.network.timeout=600s
  --conf spark.executor.heartbeatInterval=60s
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.2
  jobs/kafka_events_to_hive_ods.py
  --config "${CONFIG_PATH}"
  --run-mode "${RUN_MODE}"
)

if [[ -n "${CHECKPOINT_ROOT}" ]]; then
  CMD+=(--checkpoint-root "${CHECKPOINT_ROOT}")
fi

if [[ -n "${TRIGGER_PROCESSING_TIME}" ]]; then
  CMD+=(--trigger-processing-time "${TRIGGER_PROCESSING_TIME}")
fi

printf 'Running command:\n%s\n' "${CMD[*]}"
"${CMD[@]}"
