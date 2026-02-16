#!/bin/bash
# ============================================================
# Spark任务提交脚本
# 支持任务: hot_movies_calc, rating_sync, realtime_recs
# ============================================================

set -e

# 默认配置
SPARK_HOME=${SPARK_HOME:-/opt/spark}
JOB_DIR="$(cd "$(dirname "$0")"; pwd)"
CONF_FILE="${JOB_DIR}/conf/spark-env.conf"

# 加载配置文件
if [ -f "$CONF_FILE" ]; then
    source "$CONF_FILE"
fi

# 使用环境变量或默认值
HIVE_DB=${HIVE_DB:-ods_movie_db}
JDBC_URL=${JDBC_URL:-jdbc:mysql://localhost:3306/movie_db?useSSL=false}
DB_USER=${DB_USER:-root}
DB_PASSWORD=${DB_PASSWORD:-}
KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-localhost:9092}
KAFKA_TOPICS=${KAFKA_TOPICS:-movie-view-history,movie-rating-events,movie-comment-events,movie-favorite-events}
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_DB=${REDIS_DB:-0}
REDIS_PASSWORD=${REDIS_PASSWORD:-}
CHECKPOINT_DIR=${CHECKPOINT_DIR:-/tmp/spark/checkpoints/realtime_recs}
KAFKA_PACKAGES=${KAFKA_PACKAGES:-org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.2}

# 打印用法
usage() {
    echo "用法: $0 <任务名称> [选项]"
    echo ""
    echo "支持的任务:"
    echo "  hot_movies_calc    - 电影热度计算"
    echo "  rating_sync        - 评分融合同步"
    echo "  realtime_recs      - 实时推荐 (Kafka + Redis)"
    echo ""
    echo "选项:"
    echo "  --date DATE        - 计算日期 (yyyy-MM-dd), 默认昨天"
    echo "  --conf KEY=VALUE   - 覆盖配置项"
    echo "  --dry-run          - 仅打印命令, 不执行"
    echo ""
    echo "示例:"
    echo "  $0 hot_movies_calc --date 2026-02-12"
    echo "  $0 rating_sync --conf JDBC_URL=jdbc:mysql://newhost:3306/movie_db"
    exit 1
}

# 解析参数
TASK_NAME=$1
shift

if [ -z "$TASK_NAME" ]; then
    usage
fi

# 解析选项
CALC_DATE=""
DRY_RUN=false
EXTRA_PARAMS=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --date)
            CALC_DATE="$2"
            shift 2
            ;;
        --conf)
            IFS='=' read -r KEY VALUE <<< "$2"
            export "$KEY=$VALUE"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help)
            usage
            ;;
        *)
            EXTRA_PARAMS="$EXTRA_PARAMS $1"
            shift
            ;;
    esac
done

# 默认日期为昨天
if [ -z "$CALC_DATE" ]; then
    CALC_DATE=$(date -d "yesterday" +%Y-%m-%d 2>/dev/null || date -v-1d +%Y-%m-%d)
fi

echo "========================================"
echo "Spark任务提交"
echo "========================================"
echo "任务名称: $TASK_NAME"
echo "计算日期: $CALC_DATE"
echo "Hive数据库: $HIVE_DB"
echo "========================================"

# 根据任务名称构建提交命令
case $TASK_NAME in
    hot_movies_calc)
        PY_FILE="${JOB_DIR}/jobs/hot_movies_calc.py"
        APP_NAME="MovieHotScoreCalc_${CALC_DATE}"
        TASK_PARAMS="--calc-date ${CALC_DATE} --jdbc-url ${JDBC_URL} --db-user ${DB_USER} --db-password ${DB_PASSWORD} --hive-db ${HIVE_DB}"
        ;;
    rating_sync)
        PY_FILE="${JOB_DIR}/jobs/rating_sync.py"
        APP_NAME="MovieRatingSync_${CALC_DATE}"
        TASK_PARAMS="--jdbc-url ${JDBC_URL} --db-user ${DB_USER} --db-password ${DB_PASSWORD} --hive-db ${HIVE_DB}"
        ;;
    realtime_recs)
        PY_FILE="${JOB_DIR}/jobs/realtime_recs.py"
        APP_NAME="MovieRealtimeRecs"
        TASK_PARAMS="--kafka-bootstrap ${KAFKA_BOOTSTRAP} --kafka-topics ${KAFKA_TOPICS} --jdbc-url ${JDBC_URL} --db-user ${DB_USER} --db-password ${DB_PASSWORD} --redis-host ${REDIS_HOST} --redis-port ${REDIS_PORT} --redis-db ${REDIS_DB} --checkpoint ${CHECKPOINT_DIR}"
        if [ -n "$REDIS_PASSWORD" ]; then
            TASK_PARAMS="${TASK_PARAMS} --redis-password ${REDIS_PASSWORD}"
        fi
        ;;
    *)
        echo "错误: 未知的任务名称 '$TASK_NAME'"
        usage
        ;;
esac

# 检查Python文件是否存在
if [ ! -f "$PY_FILE" ]; then
    echo "错误: 找不到任务文件 $PY_FILE"
    exit 1
fi

# 构建Spark提交命令
SPARK_SUBMIT="${SPARK_HOME}/bin/spark-submit"

SPARK_ARGS=(
    --master yarn
    --deploy-mode cluster
    --name "$APP_NAME"
    --driver-memory 2G
    --driver-cores 1
    --executor-memory 2G
    --executor-cores 1
    --num-executors 2
    --conf spark.sql.adaptive.enabled=true
    --conf spark.sql.adaptive.coalescePartitions.enabled=true
    --conf spark.sql.adaptive.skewJoin.enabled=true
    --conf spark.sql.hive.convertMetastoreParquet=false
    --conf spark.hadoop.hive.exec.dynamic.partition=true
    --conf spark.hadoop.hive.exec.dynamic.partition.mode=nonstrict
)

# 实时推荐任务需要 Kafka 依赖
if [ "$TASK_NAME" == "realtime_recs" ]; then
    SPARK_ARGS+=(--packages "$KAFKA_PACKAGES")
fi

# 如果是local模式调试
if [ "${SPARK_MASTER}" == "local" ]; then
    SPARK_ARGS=(
        --master "local[*]"
        --name "$APP_NAME"
        --driver-memory 4G
    )
fi

# 完整命令
CMD="$SPARK_SUBMIT ${SPARK_ARGS[@]} $PY_FILE $TASK_PARAMS $EXTRA_PARAMS"

echo "提交命令:"
echo "$CMD"
echo ""

if [ "$DRY_RUN" == true ]; then
    echo "[DRY RUN] 命令未实际执行"
    exit 0
fi

# 提交任务
echo "开始提交Spark任务..."
eval $CMD

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "任务提交成功!"
    echo "========================================"
else
    echo ""
    echo "========================================"
    echo "任务提交失败 (退出码: $EXIT_CODE)"
    echo "========================================"
    exit $EXIT_CODE
fi
