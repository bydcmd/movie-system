#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash validate_etl_results.sh [calc-date]
  bash validate_etl_results.sh --calc-date YYYY-MM-DD [--skip-pg]

Description:
  Validate compact Hive warehouse partitions and optional PostgreSQL sync results.

Examples:
  bash validate_etl_results.sh 2026-04-19
  bash validate_etl_results.sh --calc-date 2026-04-19
  bash validate_etl_results.sh --calc-date 2026-04-19 --skip-pg

Options:
  --calc-date      Business date used by DW / DM jobs
  --skip-pg        Skip PostgreSQL validation even if psql is available
  --help, -h       Show this help message

PostgreSQL validation:
  This script validates PostgreSQL only when:
    1) `psql` is available in PATH
    2) standard libpq env vars are set, e.g. PGHOST / PGPORT / PGDATABASE / PGUSER / PGPASSWORD
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

CALC_DATE="$(date +%F)"
SKIP_PG=false
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
    --skip-pg)
      SKIP_PG=true
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
    CALC_DATE="${POSITIONAL_ARGS[0]}"
    ;;
  *)
    usage
    exit 1
    ;;
esac

if ! is_date "${CALC_DATE}"; then
  echo "Invalid calc date: ${CALC_DATE}" >&2
  exit 1
fi

if ! command -v hive >/dev/null 2>&1; then
  echo "ERROR: hive not found in PATH" >&2
  exit 1
fi

hive_scalar() {
  local sql="$1"
  hive -S -e "${sql}" | tr -d '\r' | tail -n 1
}

check_hive_count() {
  local label="$1"
  local table_name="$2"
  local partition_date="$3"
  local count

  count="$(hive_scalar "SELECT COUNT(*) FROM ${table_name} WHERE dt='${partition_date}';")"
  if [[ -z "${count}" ]]; then
    echo "✗ ${label}: no result returned (${table_name}, dt=${partition_date})"
    return 1
  fi
  if [[ "${count}" =~ ^[0-9]+$ ]] && (( count > 0 )); then
    echo "✓ ${label}: ${count} rows (${table_name}, dt=${partition_date})"
    return 0
  fi
  echo "✗ ${label}: 0 rows (${table_name}, dt=${partition_date})"
  return 1
}

check_dw_event_fact() {
  local total_count
  local invalid_count
  local event_type_output

  total_count="$(hive_scalar "SELECT COUNT(*) FROM dw.dw_user_event_fact_di WHERE dt='${CALC_DATE}';")"
  if [[ -z "${total_count}" || ! "${total_count}" =~ ^[0-9]+$ || "${total_count}" == "0" ]]; then
    echo "✗ DW Event Fact integrity: no rows for dt=${CALC_DATE}"
    return 1
  fi

  event_type_output="$(hive -S -e "SELECT event_type, COUNT(*) FROM dw.dw_user_event_fact_di WHERE dt='${CALC_DATE}' GROUP BY event_type ORDER BY event_type;" | tr -d '\r')"
  if [[ -z "${event_type_output}" ]]; then
    echo "✗ DW Event Fact integrity: no event_type distribution for dt=${CALC_DATE}"
    return 1
  fi

  echo "✓ DW Event Fact event types:"
  while IFS= read -r line; do
    [[ -n "${line}" ]] && echo "  - ${line}"
  done <<< "${event_type_output}"

  invalid_count="$(hive_scalar "SELECT COUNT(*) FROM dw.dw_user_event_fact_di WHERE dt='${CALC_DATE}' AND (event_id IS NULL OR TRIM(event_id) = '' OR event_type IS NULL OR TRIM(event_type) = '' OR event_ts IS NULL OR ((is_view = 1 OR is_rating = 1 OR is_comment = 1 OR is_comment_like = 1 OR is_favorite = 1 OR is_watched = 1) AND movie_id IS NULL) OR (is_comment_like = 1 AND comment_id IS NULL));")"
  if [[ -z "${invalid_count}" ]]; then
    echo "✗ DW Event Fact integrity: invalid-row check returned no result"
    return 1
  fi
  if [[ "${invalid_count}" == "0" ]]; then
    echo "✓ DW Event Fact integrity: ${total_count} rows, 0 invalid rows"
    return 0
  fi

  echo "✗ DW Event Fact integrity: ${invalid_count} invalid rows"
  return 1
}

check_dm_hot_periods() {
  local period_output
  period_output="$(hive -S -e "SELECT period_type, COUNT(*) FROM dm.dm_hot_movies WHERE dt='${CALC_DATE}' GROUP BY period_type ORDER BY period_type;" | tr -d '\r')"
  if [[ -z "${period_output}" ]]; then
    echo "✗ DM Hot Movies periods: no rows for dt=${CALC_DATE}"
    return 1
  fi

  local missing=0
  local period
  for period in DAILY MONTHLY TOTAL WEEKLY; do
    if grep -q "^${period}[[:space:]]" <<<"${period_output}"; then
      echo "✓ DM Hot Movies period present: ${period}"
    else
      echo "✗ DM Hot Movies period missing: ${period}"
      missing=1
    fi
  done

  if (( missing != 0 )); then
    return 1
  fi
  return 0
}

check_dm_hot_integrity() {
  local invalid_count
  local top_output

  invalid_count="$(hive_scalar "SELECT COUNT(*) FROM dm.dm_hot_movies WHERE dt='${CALC_DATE}' AND (movie_id IS NULL OR movie_name IS NULL OR TRIM(movie_name) = '' OR period_type IS NULL OR TRIM(period_type) = '' OR rank_no <= 0 OR hot_score IS NULL OR hot_score < 0 OR view_pv < 0 OR view_uv < 0 OR rating_cnt < 0 OR comment_cnt < 0 OR comment_like_cnt < 0 OR favorite_add_cnt < 0 OR favorite_remove_cnt < 0 OR watched_cnt < 0 OR active_user_cnt < 0);")"
  if [[ -z "${invalid_count}" ]]; then
    echo "✗ DM Hot Movies integrity: invalid-row check returned no result"
    return 1
  fi

  top_output="$(hive -S -e "SELECT period_type, rank_no, movie_name, hot_score FROM dm.dm_hot_movies WHERE dt='${CALC_DATE}' ORDER BY period_type ASC, rank_no ASC LIMIT 8;" | tr -d '\r')"
  if [[ -n "${top_output}" ]]; then
    echo "✓ DM Hot Movies sample rows:"
    while IFS= read -r line; do
      [[ -n "${line}" ]] && echo "  - ${line}"
    done <<< "${top_output}"
  fi

  if [[ "${invalid_count}" == "0" ]]; then
    echo "✓ DM Hot Movies integrity: 0 invalid rows"
    return 0
  fi

  echo "✗ DM Hot Movies integrity: ${invalid_count} invalid rows"
  return 1
}

check_dm_user_retention() {
  local day_output
  local invalid_count
  local total_count

  total_count="$(hive_scalar "SELECT COUNT(*) FROM dm.dm_user_retention WHERE dt='${CALC_DATE}';")"
  if [[ -z "${total_count}" || ! "${total_count}" =~ ^[0-9]+$ || "${total_count}" == "0" ]]; then
    echo "✗ DM User Retention integrity: no rows for dt=${CALC_DATE}"
    return 1
  fi

  day_output="$(hive -S -e "SELECT retention_day, COUNT(*) FROM dm.dm_user_retention WHERE dt='${CALC_DATE}' GROUP BY retention_day ORDER BY retention_day;" | tr -d '\r')"
  if [[ -z "${day_output}" ]]; then
    echo "✗ DM User Retention integrity: no retention_day distribution for dt=${CALC_DATE}"
    return 1
  fi

  echo "✓ DM User Retention days present:"
  while IFS= read -r line; do
    [[ -n "${line}" ]] && echo "  - ${line}"
  done <<< "${day_output}"

  invalid_count="$(hive_scalar "SELECT COUNT(*) FROM dm.dm_user_retention WHERE dt='${CALC_DATE}' AND (retention_day <= 0 OR cohort_users < 0 OR retained_users < 0 OR retained_users > cohort_users OR retention_rate < 0 OR retention_rate > 1);")"
  if [[ -z "${invalid_count}" ]]; then
    echo "✗ DM User Retention integrity: invalid-row check returned no result"
    return 1
  fi
  if [[ "${invalid_count}" == "0" ]]; then
    echo "✓ DM User Retention integrity: 0 invalid rows"
    return 0
  fi

  echo "✗ DM User Retention integrity: ${invalid_count} invalid rows"
  return 1
}

check_dm_genre_preference() {
  local total_count
  local distinct_rank_count
  local invalid_count
  local top_output

  total_count="$(hive_scalar "SELECT COUNT(*) FROM dm.dm_genre_preference_1d WHERE dt='${CALC_DATE}';")"
  if [[ -z "${total_count}" || ! "${total_count}" =~ ^[0-9]+$ || "${total_count}" == "0" ]]; then
    echo "✗ DM Genre Preference integrity: no rows for dt=${CALC_DATE}"
    return 1
  fi

  distinct_rank_count="$(hive_scalar "SELECT COUNT(DISTINCT rank_no) FROM dm.dm_genre_preference_1d WHERE dt='${CALC_DATE}';")"
  invalid_count="$(hive_scalar "SELECT COUNT(*) FROM dm.dm_genre_preference_1d WHERE dt='${CALC_DATE}' AND (genre IS NULL OR TRIM(genre) = '' OR rank_no <= 0 OR movie_cnt < 0 OR view_pv < 0 OR view_uv < 0 OR rating_cnt < 0 OR watched_cnt < 0 OR hot_score_sum < 0);")"

  if [[ -z "${distinct_rank_count}" || -z "${invalid_count}" ]]; then
    echo "✗ DM Genre Preference integrity: integrity check returned no result"
    return 1
  fi

  top_output="$(hive -S -e "SELECT rank_no, genre, hot_score_sum FROM dm.dm_genre_preference_1d WHERE dt='${CALC_DATE}' ORDER BY rank_no ASC LIMIT 5;" | tr -d '\r')"
  if [[ -n "${top_output}" ]]; then
    echo "✓ DM Genre Preference top genres:"
    while IFS= read -r line; do
      [[ -n "${line}" ]] && echo "  - ${line}"
    done <<< "${top_output}"
  fi

  if [[ "${distinct_rank_count}" == "${total_count}" && "${invalid_count}" == "0" ]]; then
    echo "✓ DM Genre Preference integrity: ${total_count} rows, unique ranks, 0 invalid rows"
    return 0
  fi

  echo "✗ DM Genre Preference integrity: total_rows=${total_count}, distinct_rank_cnt=${distinct_rank_count}, invalid_rows=${invalid_count}"
  return 1
}

pg_scalar() {
  local sql="$1"
  PSQLRC=/dev/null psql -X -A -t -c "${sql}" | tr -d '\r' | tail -n 1
}

can_validate_pg() {
  if [[ "${SKIP_PG}" == "true" ]]; then
    return 1
  fi
  if ! command -v psql >/dev/null 2>&1; then
    return 1
  fi
  if [[ -z "${PGHOST:-}" || -z "${PGDATABASE:-}" || -z "${PGUSER:-}" ]]; then
    return 1
  fi
  return 0
}

check_pg_count() {
  local label="$1"
  local sql="$2"
  local count

  count="$(pg_scalar "${sql}")"
  if [[ -z "${count}" ]]; then
    echo "✗ ${label}: no result returned"
    return 1
  fi
  if [[ "${count}" =~ ^[0-9]+$ ]] && (( count > 0 )); then
    echo "✓ ${label}: ${count} rows"
    return 0
  fi
  echo "✗ ${label}: 0 rows"
  return 1
}

FAILED=0

echo "======================================"
echo "Compact ETL Validation"
echo "======================================"
echo "Calc Date:   ${CALC_DATE}"
echo "Validate PG: $([[ "${SKIP_PG}" == "true" ]] && echo "false" || echo "auto")"
echo "======================================"
echo ""

check_hive_count "DW User Event Fact" "dw.dw_user_event_fact_di" "${CALC_DATE}" || FAILED=1
check_dw_event_fact || FAILED=1

check_hive_count "DM Hot Movies" "dm.dm_hot_movies" "${CALC_DATE}" || FAILED=1
check_dm_hot_periods || FAILED=1
check_dm_hot_integrity || FAILED=1

check_hive_count "DM User Retention" "dm.dm_user_retention" "${CALC_DATE}" || FAILED=1
check_dm_user_retention || FAILED=1

check_hive_count "DM Genre Preference" "dm.dm_genre_preference_1d" "${CALC_DATE}" || FAILED=1
check_dm_genre_preference || FAILED=1

echo ""

if can_validate_pg; then
  echo "PostgreSQL validation:"
  check_pg_count "PG stats_hot_movies" "SELECT COUNT(*) FROM public.stats_hot_movies WHERE calc_date = DATE '${CALC_DATE}';" || FAILED=1
  check_pg_count "PG stats_user_retention" "SELECT COUNT(*) FROM public.stats_user_retention WHERE calc_date = DATE '${CALC_DATE}';" || FAILED=1
  check_pg_count "PG stats_genre_preference_1d" "SELECT COUNT(*) FROM public.stats_genre_preference_1d WHERE calc_date = DATE '${CALC_DATE}';" || FAILED=1
else
  echo "PostgreSQL validation skipped (set PGHOST / PGDATABASE / PGUSER / PGPASSWORD and ensure psql is installed)."
fi

echo ""
echo "======================================"
if (( FAILED == 0 )); then
  echo "Validation result: ✓ PASSED"
  echo "======================================"
  exit 0
else
  echo "Validation result: ✗ FAILED"
  echo "======================================"
  exit 1
fi
