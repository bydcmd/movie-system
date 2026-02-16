#!/bin/bash
# ============================================================
# ODS层数据同步脚本 - 从MySQL同步到Hive ODS
# 用法: ./data_sync_script.sh [date]
# 示例: ./data_sync_script.sh 2026-02-12
# ============================================================

# 参数设置
SYNC_DATE=${1:-$(date +%Y-%m-%d)}
HIVE_DB="ods_movie_db"
MYSQL_HOST="your_mysql_host"
MYSQL_PORT="3306"
MYSQL_DB="movie_db"
MYSQL_USER="your_username"
MYSQL_PASS="your_password"
HIVE_WAREHOUSE="/warehouse/ods/ods_movie_db"

# 日志输出
echo "========================================"
echo "开始同步数据到ODS层 - 日期: $SYNC_DATE"
echo "========================================"

# ============================================================
# 函数: 全量同步
# ============================================================
full_sync() {
    local table_name=$1
    local mysql_table=$2
    local hive_table=$3
    
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 全量同步: $mysql_table -> $hive_table"
    
    # 使用Sqoop进行全量导入
    sqoop import \
        --connect "jdbc:mysql://$MYSQL_HOST:$MYSQL_PORT/$MYSQL_DB" \
        --username "$MYSQL_USER" \
        --password "$MYSQL_PASS" \
        --table "$mysql_table" \
        --target-dir "$HIVE_WAREHOUSE/$hive_table/dt=$SYNC_DATE" \
        --delete-target-dir \
        --fields-terminated-by '\t' \
        --lines-terminated-by '\n' \
        --null-string '\\N' \
        --null-non-string '\\N' \
        -m 1
    
    # 添加分区
    hive -e "
        ALTER TABLE $HIVE_DB.$hive_table ADD IF NOT EXISTS PARTITION (dt='$SYNC_DATE')
        LOCATION '$HIVE_WAREHOUSE/$hive_table/dt=$SYNC_DATE';
    "
    
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 完成: $hive_table"
}

# ============================================================
# 函数: 增量同步 (基于时间戳)
# ============================================================
incremental_sync() {
    local table_name=$1
    local mysql_table=$2
    local hive_table=$3
    local time_column=$4
    
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 增量同步: $mysql_table -> $hive_table"
    
    # 使用Sqoop进行增量导入
    sqoop import \
        --connect "jdbc:mysql://$MYSQL_HOST:$MYSQL_PORT/$MYSQL_DB" \
        --username "$MYSQL_USER" \
        --password "$MYSQL_PASS" \
        --table "$mysql_table" \
        --target-dir "$HIVE_WAREHOUSE/$hive_table/dt=$SYNC_DATE" \
        --delete-target-dir \
        --fields-terminated-by '\t' \
        --lines-terminated-by '\n' \
        --null-string '\\N' \
        --null-non-string '\\N' \
        --where "$time_column >= '$SYNC_DATE 00:00:00' AND $time_column < DATE_ADD('$SYNC_DATE', INTERVAL 1 DAY)" \
        -m 1
    
    # 添加分区
    hive -e "
        ALTER TABLE $HIVE_DB.$hive_table ADD IF NOT EXISTS PARTITION (dt='$SYNC_DATE')
        LOCATION '$HIVE_WAREHOUSE/$hive_table/dt=$SYNC_DATE';
    "
    
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 完成: $hive_table"
}

# ============================================================
# 维度表 - 全量同步 (数据量小，每日全量)
# ============================================================
echo ""
echo ">>> 开始维度表全量同步..."
full_sync "genres" "genres" "ods_genres"
full_sync "regions" "regions" "ods_regions"
full_sync "movie_genre_relation" "movie_genre_relation" "ods_movie_genre_relation"
full_sync "movie_region_relation" "movie_region_relation" "ods_movie_region_relation"

# ============================================================
# 基础数据表 - 全量同步
# ============================================================
echo ""
echo ">>> 开始基础数据表全量同步..."
full_sync "movies" "movies" "ods_movies"
full_sync "persons" "persons" "ods_persons"
full_sync "users" "users" "ods_users"
full_sync "favorite_folders" "favorite_folders" "ods_favorite_folders"
full_sync "stats_hot_movies" "stats_hot_movies" "ods_stats_hot_movies"

# ============================================================
# 行为数据表 - 增量同步 (数据量大，按日增量)
# ============================================================
echo ""
echo ">>> 开始行为数据表增量同步..."
incremental_sync "ratings" "ratings" "ods_ratings" "rating_time"
incremental_sync "comments" "comments" "ods_comments" "comment_time"
incremental_sync "comment_likes" "comment_likes" "ods_comment_likes" "create_time"
incremental_sync "favorites" "favorites" "ods_favorites" "create_time"
incremental_sync "view_history" "view_history" "ods_view_history" "view_time"

# ============================================================
# 数据质量检查
# ============================================================
echo ""
echo ">>> 开始数据质量检查..."

hive -e "
USE $HIVE_DB;

-- 检查各表分区数据量
SELECT 'ods_users' as table_name, COUNT(*) as cnt FROM ods_users WHERE dt='$SYNC_DATE'
UNION ALL
SELECT 'ods_movies', COUNT(*) FROM ods_movies WHERE dt='$SYNC_DATE'
UNION ALL
SELECT 'ods_ratings', COUNT(*) FROM ods_ratings WHERE dt='$SYNC_DATE'
UNION ALL
SELECT 'ods_comments', COUNT(*) FROM ods_comments WHERE dt='$SYNC_DATE'
UNION ALL
SELECT 'ods_favorites', COUNT(*) FROM ods_favorites WHERE dt='$SYNC_DATE'
UNION ALL
SELECT 'ods_view_history', COUNT(*) FROM ods_view_history WHERE dt='$SYNC_DATE';
"

echo ""
echo "========================================"
echo "数据同步完成 - 日期: $SYNC_DATE"
echo "========================================"
