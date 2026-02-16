-- ============================================================
-- Hive ODS 层表结构设计 - 电影系统
-- 数据源: MySQL (movie_db)
-- 层级: ODS (Operational Data Store)
-- 特点: 贴源存储，保留原始数据格式，添加ETL追踪字段
-- ============================================================

-- 创建ODS层数据库
CREATE DATABASE IF NOT EXISTS ods_movie_db
COMMENT '电影系统ODS层数据库'
LOCATION '/warehouse/ods/ods_movie_db';

USE ods_movie_db;

-- ============================================================
-- 1. 用户表 (users)
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_users (
    user_id                 STRING      COMMENT '用户唯一标识',
    user_nickname           STRING      COMMENT '用户昵称',
    user_password           STRING      COMMENT '登录密码(加密)',
    user_avatar             STRING      COMMENT '用户头像URL',
    user_url                STRING      COMMENT '豆瓣个人主页URL',
    role                    TINYINT     COMMENT '角色: 0管理员, 1普通用户',
    status                  INT         COMMENT '账号状态 (0:正常, 1:被禁用, 2:注销)',
    password_version        INT         COMMENT '密码版本号',
    email                   STRING      COMMENT '用户邮箱',
    create_time             TIMESTAMP   COMMENT '用户创建时间',
    update_time             TIMESTAMP   COMMENT '用户修改时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期(分区字段)'
)
COMMENT 'ODS层-用户表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL users表同步，每日全量或增量抽取'
);

-- ============================================================
-- 2. 电影表 (movies)
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_movies (
    movie_id                BIGINT      COMMENT '电影唯一ID',
    name                    STRING      COMMENT '电影名称',
    alias                   STRING      COMMENT '别名',
    actors                  STRING      COMMENT '演员列表(JSON格式)',
    cover                   STRING      COMMENT '封面图URL',
    directors               STRING      COMMENT '导演列表(JSON格式)',
    douban_score            DECIMAL(3,1) COMMENT '豆瓣评分',
    score                   DECIMAL(3,1) COMMENT '本站评分',
    douban_votes            INT         COMMENT '豆瓣评分人数',
    votes                   INT         COMMENT '本站评分人数',
    genres                  STRING      COMMENT '类型',
    imdb_id                 STRING      COMMENT 'IMDB ID',
    languages               STRING      COMMENT '语言',
    mins                    STRING      COMMENT '片长',
    regions                 STRING      COMMENT '地区',
    release_date            STRING      COMMENT '上映日期',
    storyline               STRING      COMMENT '剧情简介',
    year                    INT         COMMENT '年份',
    writers                 STRING      COMMENT '编剧列表(JSON格式)',
    rating_weights          STRING      COMMENT '评分分布(JSON格式)',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-电影表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL movies表同步，每日全量或增量抽取'
);

-- ============================================================
-- 3. 影人表 (persons)
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_persons (
    person_id               BIGINT      COMMENT '影人唯一ID',
    name                    STRING      COMMENT '姓名',
    sex                     STRING      COMMENT '性别',
    name_en                 STRING      COMMENT '英文名',
    name_zh                 STRING      COMMENT '中文名/原名',
    birth                   STRING      COMMENT '生日',
    birthplace              STRING      COMMENT '出生地',
    profession              STRING      COMMENT '职业',
    biography               STRING      COMMENT '个人简介',
    person_avatar           STRING      COMMENT '头像URL',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-影人表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL persons表同步，每日全量或增量抽取'
);

-- ============================================================
-- 4. 评分表 (ratings) - 增量表
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_ratings (
    user_id                 STRING      COMMENT '用户ID',
    movie_id                BIGINT      COMMENT '电影ID',
    rating                  INT         COMMENT '分值(10-50)',
    rating_time             TIMESTAMP   COMMENT '评分时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-评分表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL ratings表同步，每日增量抽取'
);

-- ============================================================
-- 5. 评论表 (comments) - 增量表
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_comments (
    comment_id              BIGINT      COMMENT '评论唯一ID',
    user_id                 STRING      COMMENT '关联用户ID',
    movie_id                BIGINT      COMMENT '关联电影ID',
    content                 STRING      COMMENT '评论内容',
    votes                   INT         COMMENT '有用点赞数',
    comment_time            TIMESTAMP   COMMENT '评论时间',
    title                   STRING      COMMENT '评论标题(长评专用)',
    type                    TINYINT     COMMENT '评论类型: 1-短评, 2-长评',
    version                 INT         COMMENT '乐观锁版本号',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-评论表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL comments表同步，每日增量抽取'
);

-- ============================================================
-- 6. 评论点赞表 (comment_likes) - 增量表
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_comment_likes (
    id                      BIGINT      COMMENT '主键ID',
    comment_id              BIGINT      COMMENT '评论ID',
    user_id                 STRING      COMMENT '用户ID',
    create_time             TIMESTAMP   COMMENT '创建时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-评论点赞表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL comment_likes表同步，每日增量抽取'
);

-- ============================================================
-- 7. 收藏表 (favorites) - 增量表
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_favorites (
    user_id                 STRING      COMMENT '用户ID',
    movie_id                BIGINT      COMMENT '电影ID',
    folder_id               BIGINT      COMMENT '收藏夹ID，0表示默认收藏夹',
    create_time             TIMESTAMP   COMMENT '收藏时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-收藏表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL favorites表同步，每日增量抽取'
);

-- ============================================================
-- 8. 收藏夹表 (favorite_folders)
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_favorite_folders (
    id                      BIGINT      COMMENT '收藏夹ID',
    user_id                 STRING      COMMENT '用户ID',
    name                    STRING      COMMENT '收藏夹名称',
    description             STRING      COMMENT '收藏夹描述',
    is_public               TINYINT     COMMENT '是否公开：0-私密, 1-公开',
    movie_count             INT         COMMENT '电影数量',
    create_time             TIMESTAMP   COMMENT '创建时间',
    update_time             TIMESTAMP   COMMENT '更新时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-收藏夹表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL favorite_folders表同步，每日全量或增量抽取'
);

-- ============================================================
-- 9. 浏览历史表 (view_history) - 增量表
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_view_history (
    history_id              BIGINT      COMMENT '历史记录ID',
    user_id                 STRING      COMMENT '用户ID',
    movie_id                BIGINT      COMMENT '电影ID',
    view_time               TIMESTAMP   COMMENT '浏览时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-浏览历史表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL view_history表同步，每日增量抽取'
);

-- ============================================================
-- 10. 类型表 (genres) - 维度表
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_genres (
    id                      INT         COMMENT '类型ID',
    name                    STRING      COMMENT '类型名称',
    name_en                 STRING      COMMENT '英文名称',
    description             STRING      COMMENT '类型描述',
    create_time             TIMESTAMP   COMMENT '创建时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-电影类型维度表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL genres表同步，每日全量抽取'
);

-- ============================================================
-- 11. 地区表 (regions) - 维度表
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_regions (
    id                      INT         COMMENT '地区ID',
    name                    STRING      COMMENT '地区名称',
    name_en                 STRING      COMMENT '英文名称',
    description             STRING      COMMENT '地区描述',
    create_time             TIMESTAMP   COMMENT '创建时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-电影地区维度表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL regions表同步，每日全量抽取'
);

-- ============================================================
-- 12. 电影类型关联表 (movie_genre_relation)
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_movie_genre_relation (
    id                      BIGINT      COMMENT '关系ID',
    movie_id                BIGINT      COMMENT '电影ID',
    genre_id                INT         COMMENT '类型ID',
    create_time             TIMESTAMP   COMMENT '创建时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-电影类型关联表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL movie_genre_relation表同步，每日全量抽取'
);

-- ============================================================
-- 13. 电影地区关联表 (movie_region_relation)
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_movie_region_relation (
    id                      BIGINT      COMMENT '关系ID',
    movie_id                BIGINT      COMMENT '电影ID',
    region_id               INT         COMMENT '地区ID',
    create_time             TIMESTAMP   COMMENT '创建时间',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-电影地区关联表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL movie_region_relation表同步，每日全量抽取'
);

-- ============================================================
-- 14. 热度统计表 (stats_hot_movies) - Spark计算结果
-- ============================================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_stats_hot_movies (
    id                      BIGINT      COMMENT '主键ID',
    movie_id                BIGINT      COMMENT '电影ID',
    period_type             STRING      COMMENT '统计周期: DAILY/WEEKLY/MONTHLY',
    hot_score               DOUBLE      COMMENT '热度分值',
    calc_date               STRING      COMMENT '计算日期',
    -- ETL追踪字段
    etl_time                TIMESTAMP   COMMENT '数据抽取时间',
    source_table            STRING      COMMENT '源表名',
    etl_date                STRING      COMMENT 'ETL日期'
)
COMMENT 'ODS层-电影热度统计表'
PARTITIONED BY (dt STRING COMMENT '日期分区,格式:yyyy-MM-dd')
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'ZLIB',
    'comment' = '从MySQL stats_hot_movies表同步，每日全量抽取'
);
