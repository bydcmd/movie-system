#!/usr/bin/env bash
#
# Compact ETL Pipeline Script - Run the 4-table warehouse workflow
#

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash etl.sh [calc-date] [config-path]
  bash etl.sh [config-path]
  bash etl.sh --calc-date YYYY-MM-DD --config conf/etl_config.json [--generate-source] [--skip-dwd] [--skip-ads]

Compact Pipeline Steps:
  0. Source: Generate synthetic PostgreSQL source data (optional, run_dw_source_data.sh)
  1. DW: Build user event fact table (run_dw_user_event_fact.sh)
  2. DM: Build hot movies (run_dm_hot_movies.sh)
  3. DM: Build user retention (run_dm_user_retention.sh)
  4. DM: Build genre preference (run_dm_genre_preference.sh)
  5. DM: Sync to PostgreSQL (run_dm_pg_sync.sh)

Options:
  --generate-source  Generate synthetic PostgreSQL source data before compact warehouse build
  --skip-dwd      Skip DW fact table job
  --skip-ads      Skip DM result jobs
  --skip-sync     Skip final PostgreSQL sync
  --help, -h      Show this help message

Examples:
  bash etl.sh                                    # Run full pipeline with today's date
  bash etl.sh 2026-03-25                         # Run full pipeline for specific date
  bash etl.sh conf/etl_config.dev.json           # Run with custom config
  bash etl.sh 2026-03-25 conf/etl_config.dev.json
  bash etl.sh --generate-source --calc-date 2026-03-25
  bash etl.sh --skip-dwd --calc-date 2026-03-25  # Skip fact table, run DM jobs
  bash etl.sh --skip-sync                        # Run everything except final PG sync
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

CALC_DATE="$(date +%F)"
CONFIG_PATH="conf/etl_config.json"
GENERATE_SOURCE=false
SKIP_ODS=true
SKIP_DWD=false
SKIP_DWS=true
SKIP_ADS=false
SKIP_SYNC=false
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
    --generate-source)
      GENERATE_SOURCE=true
      shift
      ;;
    --skip-ods)
      SKIP_ODS=true
      shift
      ;;
    --skip-dwd)
      SKIP_DWD=true
      shift
      ;;
    --skip-dws)
      SKIP_DWS=true
      shift
      ;;
    --skip-ads)
      SKIP_ADS=true
      shift
      ;;
    --skip-sync)
      SKIP_SYNC=true
      shift
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
  echo "ERROR: spark-submit not found in PATH" >&2
  exit 1
fi

if [[ ! -f "${CONFIG_PATH}" ]]; then
  echo "ERROR: Config file not found: ${CONFIG_PATH}" >&2
  exit 1
fi

echo "======================================"
echo "Movie Analytics ETL Pipeline"
echo "======================================"
echo "Calc Date:    ${CALC_DATE}"
echo "Config Path:  ${CONFIG_PATH}"
echo "Generate Src: ${GENERATE_SOURCE}"
echo "Skip ODS:     ${SKIP_ODS}"
echo "Skip DWD:     ${SKIP_DWD}"
echo "Skip DWS:     ${SKIP_DWS}"
echo "Skip ADS:     ${SKIP_ADS}"
echo "Skip Sync:    ${SKIP_SYNC}"
echo "======================================"
echo ""

# Track overall success
OVERALL_SUCCESS=true
FAILED_JOBS=()

# Helper function to run a job
run_job() {
  local job_name="$1"
  local script_name="$2"
  shift 2
  local extra_args=("$@")

  echo ""
  echo "--------------------------------------"
  echo "Running: ${job_name}"
  echo "--------------------------------------"

  if bash "${SCRIPT_DIR}/${script_name}" "${CALC_DATE}" "${CONFIG_PATH}" "${extra_args[@]}"; then
    echo "✓ ${job_name} completed successfully"
  else
    echo "✗ ${job_name} FAILED"
    FAILED_JOBS+=("${job_name}")
    OVERALL_SUCCESS=false
    # Continue with next job unless it's a critical failure
  fi
}

# ============================================
# LAYER 0: Source Data Generation (Optional)
# ============================================
if [[ "${GENERATE_SOURCE}" == "true" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 0: Source - Synthetic Data"
  echo "######################################"

  run_job "Generate PostgreSQL Source Data" "run_dw_source_data.sh"
else
  echo ""
  echo "# Skipping synthetic source generation (enable with --generate-source)"
fi

# ============================================
# LAYER 1: DW (Compact Fact)
# ============================================
if [[ "${SKIP_DWD}" == "false" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 1: DW - Compact Event Fact"
  echo "######################################"

  run_job "DW User Event Fact" "run_dw_user_event_fact.sh"
else
  echo ""
  echo "# Skipping DW fact layer (--skip-dwd)"
fi

# ============================================
# LAYER 2: DM (Data Mart)
# ============================================
if [[ "${SKIP_ADS}" == "false" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 2: DM - Analytics & Reports"
  echo "######################################"

  run_job "DM Hot Movies" "run_dm_hot_movies.sh"
  run_job "DM User Retention" "run_dm_user_retention.sh"
  run_job "DM Genre Preference" "run_dm_genre_preference.sh"

else
  echo ""
  echo "# Skipping DM layer (--skip-ads)"
fi

# ============================================
# LAYER 3: Sync to PostgreSQL
# ============================================
if [[ "${SKIP_SYNC}" == "false" && "${SKIP_ADS}" == "false" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 3: PostgreSQL Sync"
  echo "######################################"

  run_job "DM PostgreSQL Sync" "run_dm_pg_sync.sh"
else
  echo ""
  echo "# Skipping PostgreSQL sync (--skip-sync or --skip-ads)"
fi

# ============================================
# Summary
# ============================================
echo ""
echo "======================================"
echo "ETL Pipeline Summary"
echo "======================================"

if [[ "${OVERALL_SUCCESS}" == "true" ]]; then
  echo "Status: ✓ ALL JOBS COMPLETED SUCCESSFULLY"
  echo "======================================"
  exit 0
else
  echo "Status: ✗ SOME JOBS FAILED"
  echo "======================================"
  echo ""
  echo "Failed jobs:"
  for job in "${FAILED_JOBS[@]}"; do
    echo "  - ${job}"
  done
  echo ""
  exit 1
fi
