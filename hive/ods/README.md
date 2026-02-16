# Hive ODS 层设计文档

## 一、概述

### 1.1 ODS层定义
ODS (Operational Data Store) 操作数据存储层，是数据仓库的第一层，主要职责：
- **贴源存储**: 保持与源系统(MySQL)一致的数据结构
- **数据缓冲**: 作为数据入仓的第一站，便于后续处理和回溯
- **历史追溯**: 通过分区保存历史数据快照

### 1.2 设计原则
| 原则 | 说明 |
|------|------|
| 保持原貌 | 字段名、数据类型尽量与源表一致 |
| 添加审计 | 统一添加ETL追踪字段(etl_time, source_table等) |
| 分区存储 | 按日期分区(dt)，便于管理和清理 |
| 压缩存储 | 使用ORC格式+ZLIB压缩，节省存储空间 |
| 外部表 | 使用EXTERNAL TABLE，数据与元数据分离 |

---

## 二、表结构清单

### 2.1 用户相关

| 表名 | 源表 | 同步方式 | 说明 |
|------|------|----------|------|
| ods_users | users | 全量 | 用户信息 |
| ods_favorites | favorites | 增量 | 收藏记录 |
| ods_favorite_folders | favorite_folders | 全量 | 收藏夹信息 |
| ods_view_history | view_history | 增量 | 浏览历史 |

### 2.2 电影内容相关

| 表名 | 源表 | 同步方式 | 说明 |
|------|------|----------|------|
| ods_movies | movies | 全量 | 电影基本信息 |
| ods_persons | persons | 全量 | 影人信息 |
| ods_genres | genres | 全量(小表) | 电影类型维度 |
| ods_regions | regions | 全量(小表) | 地区维度 |
| ods_movie_genre_relation | movie_genre_relation | 全量 | 电影类型关联 |
| ods_movie_region_relation | movie_region_relation | 全量 | 电影地区关联 |

### 2.3 用户行为相关

| 表名 | 源表 | 同步方式 | 说明 |
|------|------|----------|------|
| ods_ratings | ratings | 增量 | 评分记录 |
| ods_comments | comments | 增量 | 评论内容 |
| ods_comment_likes | comment_likes | 增量 | 评论点赞 |

### 2.4 统计数据

| 表名 | 源表 | 同步方式 | 说明 |
|------|------|----------|------|
| ods_stats_hot_movies | stats_hot_movies | 全量 | 热度统计(Spark计算结果) |

---

## 三、ETL追踪字段说明

每张表都包含以下标准ETL字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| etl_time | TIMESTAMP | 数据抽取时间，记录何时进入ODS |
| source_table | STRING | 源表名，便于追溯数据来源 |
| etl_date | STRING | ETL日期，与分区字段一致 |
| dt | STRING | 分区字段，格式: yyyy-MM-dd |

---

## 四、同步策略

### 4.1 全量同步
适用表: `users`, `movies`, `persons`, `genres`, `regions`, `favorite_folders`, `*_relation`, `stats_hot_movies`

```bash
# 特点
- 每日删除旧分区，重新导入全量数据
- 适用于数据量不大(百万级以下)或更新频繁的表
- 保证数据一致性，逻辑简单
```

### 4.2 增量同步
适用表: `ratings`, `comments`, `comment_likes`, `favorites`, `view_history`

```bash
# 特点
- 基于时间字段抽取当日新增数据
- 适用于数据量大、只增不改的流水表
- 节省存储和计算资源

# 增量条件示例
WHERE rating_time >= '2026-02-12 00:00:00' 
  AND rating_time < '2026-02-13 00:00:00'
```

---

## 五、常用查询示例

### 5.1 查看表分区
```sql
SHOW PARTITIONS ods_movie_db.ods_ratings;
```

### 5.2 查看某日数据量
```sql
SELECT COUNT(*) FROM ods_movie_db.ods_ratings 
WHERE dt = '2026-02-12';
```

### 5.3 查看最新分区数据
```sql
SELECT * FROM ods_movie_db.ods_movies 
WHERE dt = '${hiveconf:current_date}'
LIMIT 10;
```

### 5.4 多表关联查询
```sql
-- 查询某用户的评分记录(含电影信息)
SELECT 
    r.user_id,
    r.rating,
    m.name as movie_name,
    r.rating_time
FROM ods_movie_db.ods_ratings r
JOIN ods_movie_db.ods_movies m 
    ON r.movie_id = m.movie_id 
    AND r.dt = '2026-02-12'
    AND m.dt = '2026-02-12'
WHERE r.user_id = 'xxx'
LIMIT 100;
```

---

## 六、数据质量检查

### 6.1 主键唯一性检查
```sql
-- 检查movies表主键是否唯一
SELECT movie_id, COUNT(*) as cnt
FROM ods_movie_db.ods_movies
WHERE dt = '2026-02-12'
GROUP BY movie_id
HAVING COUNT(*) > 1;
```

### 6.2 数据完整性检查
```sql
-- 检查ratings是否有对应的movies
SELECT COUNT(*) as invalid_count
FROM ods_movie_db.ods_ratings r
LEFT JOIN ods_movie_db.ods_movies m 
    ON r.movie_id = m.movie_id 
    AND m.dt = '2026-02-12'
WHERE r.dt = '2026-02-12'
  AND m.movie_id IS NULL;
```

### 6.3 数据量波动检查
```sql
-- 对比近两日数据量
SELECT 
    dt,
    COUNT(*) as cnt,
    LAG(COUNT(*)) OVER (ORDER BY dt) as prev_cnt,
    (COUNT(*) - LAG(COUNT(*)) OVER (ORDER BY dt)) / LAG(COUNT(*)) OVER (ORDER BY dt) * 100 as growth_rate
FROM ods_movie_db.ods_ratings
GROUP BY dt
ORDER BY dt DESC
LIMIT 7;
```

---

## 七、运维管理

### 7.1 分区清理(保留最近30天)
```bash
#!/bin/bash
# 清理30天前的分区
RETENTION_DAYS=30
CLEAN_DATE=$(date -d "$RETENTION_DAYS days ago" +%Y-%m-%d)

hive -e "
    ALTER TABLE ods_movie_db.ods_ratings 
    DROP IF EXISTS PARTITION (dt < '$CLEAN_DATE');
"
```

### 7.2 表统计信息收集
```sql
-- 收集统计信息，优化查询性能
ANALYZE TABLE ods_movie_db.ods_movies 
PARTITION(dt='2026-02-12') 
COMPUTE STATISTICS;
```

### 7.3 表结构变更处理
```sql
-- 添加新列示例
ALTER TABLE ods_movie_db.ods_users 
ADD COLUMNS (phone STRING COMMENT '手机号');
```

---

## 八、目录结构

```
hive/
├── ods/                          # ODS层
│   ├── ods_movie_db.hql         # 建表语句
│   ├── data_sync_script.sh      # 数据同步脚本
│   └── README.md                # 本说明文档
├── dwd/                         # DWD层(明细数据层)
├── dws/                         # DWS层(汇总数据层)
└── ads/                         # ADS层(应用数据层)
```

---

## 九、注意事项

1. **JSON字段处理**: `actors`, `directors`, `writers`, `rating_weights` 等字段在ODS层保持JSON字符串格式，在DWD层解析为结构化数据

2. **时间字段**: MySQL的DATETIME映射为Hive的TIMESTAMP，注意时区问题

3. **Decimal精度**: 评分字段使用DECIMAL(3,1)，与源表保持一致

4. **特殊字符**: 评论内容等TEXT字段可能包含换行符，导入时需处理

5. **分区加载**: 使用Sqoop导入后，需要执行`ADD PARTITION`或`MSCK REPAIR TABLE`才能查询到数据
