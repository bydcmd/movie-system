#!/usr/bin/env bash
#
# Full ETL Pipeline Script - Run the complete data workflow from ODS to ADS
#

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash etl.sh [calc-date] [config-path]
  bash etl.sh [config-path]
  bash etl.sh --calc-date YYYY-MM-DD --config conf/etl_config.json [--generate-source] [--skip-ods] [--skip-dwd] [--skip-dws] [--skip-ads]

Full Pipeline Steps:
  0. Source: Generate synthetic PostgreSQL source data (optional, run_generate_dwd_source_data.sh)
  1. ODS: PostgreSQL sync (run_postgres_sync.sh)
  2. DWD: Build event wide table (run_dwd_build.sh)
  3. DWD: Build snapshots (run_dwd_snapshots.sh)
  4. DWS: Build interactions (run_dws_postgres_interactions.sh)
  5. ADS: Build hot movies (run_ads_hot_movies.sh)
  6. ADS: Build ItemCF recommendations (run_ads_itemcf.sh)
  7. ADS: Build user retention (run_ads_user_retention.sh)
  8. ADS: Build genre preference (run_ads_genre_preference.sh)
  9. ADS: Sync to PostgreSQL (run_ads_pg_sync.sh)

Options:
  --generate-source  Generate synthetic PostgreSQL source data before ODS sync
  --skip-ods      Skip ODS layer jobs
  --skip-dwd      Skip DWD layer jobs
  --skip-dws      Skip DWS layer jobs
  --skip-ads      Skip ADS layer jobs
  --skip-sync     Skip final PostgreSQL sync
  --help, -h      Show this help message

Examples:
  bash etl.sh                                    # Run full pipeline with today's date
  bash etl.sh 2026-03-25                         # Run full pipeline for specific date
  bash etl.sh conf/etl_config.dev.json           # Run with custom config
  bash etl.sh 2026-03-25 conf/etl_config.dev.json
  bash etl.sh --generate-source --calc-date 2026-03-25
  bash etl.sh --skip-ods --calc-date 2026-03-25  # Skip ODS, run from DWD onwards
  bash etl.sh --skip-sync                        # Run everything except final PG sync
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

CALC_DATE="$(date +%F)"
CONFIG_PATH="conf/etl_config.json"
GENERATE_SOURCE=false
SKIP_ODS=false
SKIP_DWD=false
SKIP_DWS=false
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

  run_job "Generate PostgreSQL Source Data" "run_generate_dwd_source_data.sh"
else
  echo ""
  echo "# Skipping synthetic source generation (enable with --generate-source)"
fi

# ============================================
# LAYER 1: ODS (Operational Data Store)
# ============================================
if [[ "${SKIP_ODS}" == "false" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 1: ODS - Data Ingestion"
  echo "######################################"

  # PostgreSQL snapshot sync
  run_job "ODS PostgreSQL Sync" "run_postgres_sync.sh"
else
  echo ""
  echo "# Skipping ODS layer (--skip-ods)"
fi

# ============================================
# LAYER 2: DWD (Data Warehouse Detail)
# ============================================
if [[ "${SKIP_DWD}" == "false" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 2: DWD - Detail Processing"
  echo "######################################"

  # Build event wide table
  run_job "DWD Event Wide Table" "run_dwd_build.sh"

  # Build snapshots
  run_job "DWD Snapshots" "run_dwd_snapshots.sh"
else
  echo ""
  echo "# Skipping DWD layer (--skip-dwd)"
fi

# ============================================
# LAYER 3: DWS (Data Warehouse Summary)
# ============================================
if [[ "${SKIP_DWS}" == "false" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 3: DWS - Aggregation Layer"
  echo "######################################"

  # Build interactions
  run_job "DWS Interactions" "run_dws_postgres_interactions.sh"
else
  echo ""
  echo "# Skipping DWS layer (--skip-dws)"
fi

# ============================================
# LAYER 4: ADS (Application Data Service)
# ============================================
if [[ "${SKIP_ADS}" == "false" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 4: ADS - Analytics & Reports"
  echo "######################################"

  # Hot movies ranking
  run_job "ADS Hot Movies" "run_ads_hot_movies.sh"

  # ItemCF recommendations
  run_job "ADS ItemCF Recommendations" "run_ads_itemcf.sh"

  # User retention
  run_job "ADS User Retention" "run_ads_user_retention.sh"

  # Genre preference
  run_job "ADS Genre Preference" "run_ads_genre_preference.sh"

else
  echo ""
  echo "# Skipping ADS layer (--skip-ads)"
fi

# ============================================
# LAYER 5: Sync to PostgreSQL
# ============================================
if [[ "${SKIP_SYNC}" == "false" && "${SKIP_ADS}" == "false" ]]; then
  echo ""
  echo "######################################"
  echo "# LAYER 5: PostgreSQL Sync"
  echo "######################################"

  # Sync ADS data back to PostgreSQL
  run_job "ADS PostgreSQL Sync" "run_ads_pg_sync.sh"
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
