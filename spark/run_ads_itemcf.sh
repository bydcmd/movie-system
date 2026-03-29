#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash run_ads_itemcf.sh [calc-date] [config-path]
  bash run_ads_itemcf.sh [config-path]
  bash run_ads_itemcf.sh --calc-date YYYY-MM-DD --config conf/etl_config.json [--top-k 100] [--top-n 30]
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

CALC_DATE="$(date +%F)"
CONFIG_PATH="conf/etl_config.json"
TOP_K=""
TOP_N=""
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
    --top-k)
      TOP_K="${2:-}"
      shift 2
      ;;
    --top-n)
      TOP_N="${2:-}"
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

CMD=(
  spark-submit
  --master yarn
  --deploy-mode client
  jobs/build_ads_itemcf_recommendations.py
  --config "${CONFIG_PATH}"
  --calc-date "${CALC_DATE}"
)

if [[ -n "${TOP_K}" ]]; then
  CMD+=(--top-k "${TOP_K}")
fi

if [[ -n "${TOP_N}" ]]; then
  CMD+=(--top-n "${TOP_N}")
fi

printf 'Running command:\n%s\n' "${CMD[*]}"
"${CMD[@]}"
