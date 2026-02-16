-- ============================================================
-- ODS层常用DDL操作合集
-- ============================================================

USE ods_movie_db;

-- ============================================================
-- 1. 分区管理
-- ============================================================

-- 1.1 查看所有表的分区信息
SHOW PARTITIONS ods_ratings;
SHOW PARTITIONS ods_movies;

-- 1.2 添加分区 (手动添加)
ALTER TABLE ods_ratings ADD PARTITION (dt='2026-02-12')
LOCATION '/warehouse/ods/ods_movie_db/ods_ratings/dt=2026-02-12';

-- 1.3 删除分区 (清理历史数据)
ALTER TABLE ods_ratings DROP IF EXISTS PARTITION (dt='2026-01-01');

-- 1.4 删除多个分区 (保留最近30天)
ALTER TABLE ods_ratings DROP IF EXISTS PARTITION (dt<'2026-01-13');

-- 1.5 修复分区 (自动识别HDFS上的分区目录)
MSCK REPAIR TABLE ods_ratings;

-- ============================================================
-- 2. 表结构变更
-- ============================================================

-- 2.1 添加列
ALTER TABLE ods_users ADD COLUMNS (phone STRING COMMENT '手机号');

-- 2.2 修改列 (仅修改注释和类型)
ALTER TABLE ods_movies CHANGE COLUMN storyline storyline STRING COMMENT '剧情简介(更新)';

-- 2.3 查看表结构
DESCRIBE FORMATTED ods_movies;
DESCRIBE ods_ratings;

-- ============================================================
-- 3. 统计信息收集
-- ============================================================

-- 3.1 收集全表统计信息
ANALYZE TABLE ods_movies COMPUTE STATISTICS;

-- 3.2 收集分区统计信息
ANALYZE TABLE ods_ratings PARTITION(dt='2026-02-12') COMPUTE STATISTICS;

-- 3.3 收集列统计信息
ANALYZE TABLE ods_movies COMPUTE STATISTICS FOR COLUMNS movie_id, name, year;

-- ============================================================
-- 4. 数据质量检查脚本
-- ============================================================

-- 4.1 检查所有表的数据量
SELECT 
    'ods_users' as table_name, 
    COUNT(*) as total_count,
    dt
FROM ods_users 
WHERE dt = '${hiveconf:current_date}'
GROUP BY dt

UNION ALL

SELECT 
    'ods_movies', 
    COUNT(*),
    dt
FROM ods_movies 
WHERE dt = '${hiveconf:current_date}'
GROUP BY dt

UNION ALL

SELECT 
    'ods_ratings', 
    COUNT(*),
    dt
FROM ods_ratings 
WHERE dt = '${hiveconf:current_date}'
GROUP BY dt

UNION ALL

SELECT 
    'ods_comments', 
    COUNT(*),
    dt
FROM ods_comments 
WHERE dt = '${hiveconf:current_date}'
GROUP BY dt

UNION ALL

SELECT 
    'ods_favorites', 
    COUNT(*),
    dt
FROM ods_favorites 
WHERE dt = '${hiveconf:current_date}'
GROUP BY dt

UNION ALL

SELECT 
    'ods_view_history', 
    COUNT(*),
    dt
FROM ods_view_history 
WHERE dt = '${hiveconf:current_date}'
GROUP BY dt;

-- 4.2 检查主键唯一性
-- movies表
SELECT 'ods_movies' as table_name, 
       COUNT(*) as duplicate_count
FROM (
    SELECT movie_id
    FROM ods_movies
    WHERE dt = '${hiveconf:current_date}'
    GROUP BY movie_id
    HAVING COUNT(*) > 1
) t;

-- users表
SELECT 'ods_users' as table_name, 
       COUNT(*) as duplicate_count
FROM (
    SELECT user_id
    FROM ods_users
    WHERE dt = '${hiveconf:current_date}'
    GROUP BY user_id
    HAVING COUNT(*) > 1
) t;

-- 4.3 检查外键关联完整性
-- ratings -> movies
SELECT 'ratings_to_movies' as check_item,
       COUNT(*) as invalid_count
FROM ods_ratings r
LEFT JOIN ods_movies m ON r.movie_id = m.movie_id 
    AND m.dt = '${hiveconf:current_date}'
WHERE r.dt = '${hiveconf:current_date}'
  AND m.movie_id IS NULL;

-- comments -> movies
SELECT 'comments_to_movies' as check_item,
       COUNT(*) as invalid_count
FROM ods_comments c
LEFT JOIN ods_movies m ON c.movie_id = m.movie_id 
    AND m.dt = '${hiveconf:current_date}'
WHERE c.dt = '${hiveconf:current_date}'
  AND m.movie_id IS NULL;

-- favorites -> movies
SELECT 'favorites_to_movies' as check_item,
       COUNT(*) as invalid_count
FROM ods_favorites f
LEFT JOIN ods_movies m ON f.movie_id = m.movie_id 
    AND m.dt = '${hiveconf:current_date}'
WHERE f.dt = '${hiveconf:current_date}'
  AND m.movie_id IS NULL;

-- 4.4 检查空值率
SELECT 
    'ods_movies' as table_name,
    COUNT(*) as total_count,
    SUM(CASE WHEN name IS NULL THEN 1 ELSE 0 END) as null_name_count,
    SUM(CASE WHEN douban_score IS NULL THEN 1 ELSE 0 END) as null_score_count,
    SUM(CASE WHEN storyline IS NULL THEN 1 ELSE 0 END) as null_storyline_count
FROM ods_movies
WHERE dt = '${hiveconf:current_date}';

-- ============================================================
-- 5. 数据抽样检查
-- ============================================================

-- 5.1 随机抽样100条
SELECT * FROM ods_movies 
WHERE dt = '${hiveconf:current_date}'
DISTRIBUTE BY RAND()
SORT BY RAND()
LIMIT 100;

-- 5.2 查看评分分布
SELECT 
    rating,
    COUNT(*) as rating_count
FROM ods_ratings
WHERE dt = '${hiveconf:current_date}'
GROUP BY rating
ORDER BY rating;

-- 5.3 查看评论类型分布
SELECT 
    type,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
FROM ods_comments
WHERE dt = '${hiveconf:current_date}'
GROUP BY type;

-- ============================================================
-- 6. 数据对比 (今日 vs 昨日)
-- ============================================================

WITH today AS (
    SELECT 
        'ratings' as metric,
        COUNT(*) as cnt
    FROM ods_ratings
    WHERE dt = '${hiveconf:current_date}'
),
yesterday AS (
    SELECT 
        'ratings' as metric,
        COUNT(*) as cnt
    FROM ods_ratings
    WHERE dt = DATE_SUB('${hiveconf:current_date}', 1)
)
SELECT 
    t.metric,
    y.cnt as yesterday_count,
    t.cnt as today_count,
    t.cnt - y.cnt as diff,
    ROUND((t.cnt - y.cnt) * 100.0 / y.cnt, 2) as growth_rate
FROM today t
JOIN yesterday y ON t.metric = y.metric;

-- ============================================================
-- 7. 存储优化
-- ============================================================

-- 7.1 小文件合并 (如果增量同步产生大量小文件)
INSERT OVERWRITE TABLE ods_ratings
PARTITION (dt)
SELECT 
    user_id,
    movie_id,
    rating,
    rating_time,
    etl_time,
    source_table,
    etl_date,
    dt
FROM ods_ratings
WHERE dt = '${hiveconf:current_date}';

-- 7.2 查看表存储大小
DESCRIBE FORMATTED ods_movies;
-- 查看 Table Parameters 中的 totalSize
