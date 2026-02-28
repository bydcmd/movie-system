/*
 Navicat Premium Dump SQL

 Source Server         : 停止線上の障壁
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44)
 Source Host           : 120.26.174.66:3306
 Source Schema         : movie_db

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44)
 File Encoding         : 65001

 Date: 12/02/2026 17:32:47
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for comment_likes
-- ----------------------------
DROP TABLE IF EXISTS `comment_likes`;
CREATE TABLE `comment_likes`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL COMMENT '评论ID',
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_comment_user`(`comment_id` ASC, `user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for comments
-- ----------------------------
DROP TABLE IF EXISTS `comments`;
CREATE TABLE `comments`  (
  `comment_id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论唯一ID（自增）',
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联用户ID',
  `movie_id` bigint NULL DEFAULT NULL COMMENT '关联电影ID',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '评论内容，短评为纯文本，长评是Tiptap的JSON格式',
  `votes` int NULL DEFAULT 0 COMMENT '有用点赞数',
  `comment_time` datetime NULL DEFAULT NULL COMMENT '评论时间',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '评论标题(长评专用)',
  `type` tinyint NULL DEFAULT 1 COMMENT '评论类型: 1-短评, 2-长评',
  `version` int NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`comment_id`) USING BTREE,
  UNIQUE INDEX `uk_user_movie_type`(`user_id` ASC, `movie_id` ASC, `type` ASC) USING BTREE,
  INDEX `idx_user_movie`(`user_id` ASC, `movie_id` ASC) USING BTREE,
  INDEX `idx_movie_id`(`movie_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1770468106665 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for favorite_folders
-- ----------------------------
DROP TABLE IF EXISTS `favorite_folders`;
CREATE TABLE `favorite_folders`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏夹ID',
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '收藏夹名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收藏夹描述',
  `is_public` tinyint NULL DEFAULT 0 COMMENT '是否公开：0-私密, 1-公开',
  `movie_count` int NULL DEFAULT 0 COMMENT '电影数量',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户自定义收藏夹' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for favorites
-- ----------------------------
DROP TABLE IF EXISTS `favorites`;
CREATE TABLE `favorites`  (
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `folder_id` bigint NOT NULL DEFAULT 0 COMMENT '收藏夹ID，0表示默认收藏夹',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`user_id`, `movie_id`, `folder_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_folder_id`(`folder_id` ASC) USING BTREE,
  INDEX `idx_movie_id`(`movie_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户收藏记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for genres
-- ----------------------------
DROP TABLE IF EXISTS `genres`;
CREATE TABLE `genres`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '类型ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类型名称',
  `name_en` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '英文名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影类型表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for movie_genre_relation
-- ----------------------------
DROP TABLE IF EXISTS `movie_genre_relation`;
CREATE TABLE `movie_genre_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `genre_id` int NOT NULL COMMENT '类型ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_movie_genre`(`movie_id` ASC, `genre_id` ASC) USING BTREE,
  INDEX `idx_movie_id`(`movie_id` ASC) USING BTREE,
  INDEX `idx_genre_id`(`genre_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 424 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影-类型关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for movie_region_relation
-- ----------------------------
DROP TABLE IF EXISTS `movie_region_relation`;
CREATE TABLE `movie_region_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `region_id` int NOT NULL COMMENT '地区ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_movie_region`(`movie_id` ASC, `region_id` ASC) USING BTREE,
  INDEX `idx_movie_id`(`movie_id` ASC) USING BTREE,
  INDEX `idx_region_id`(`region_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 236 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影-地区关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for movies
-- ----------------------------
DROP TABLE IF EXISTS `movies`;
CREATE TABLE `movies`  (
  `movie_id` bigint NOT NULL COMMENT '电影唯一ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '电影名称',
  `alias` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '别名',
  `actors` json NULL COMMENT '演员列表，有NAME和ID两个字段',
  `cover` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封面图URL',
  `directors` json NULL COMMENT '导演列表，有NAME和ID两个字段',
  `douban_score` decimal(3, 1) NULL DEFAULT NULL COMMENT '豆瓣评分',
  `score` decimal(3, 1) NULL DEFAULT NULL COMMENT '本站评分',
  `douban_votes` int NULL DEFAULT 0 COMMENT '评分人数',
  `votes` int NULL DEFAULT NULL COMMENT '本站评分人数',
  `genres` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型',
  `imdb_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'IMDB ID',
  `languages` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '语言',
  `mins` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '片长',
  `regions` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地区',
  `release_date` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '上映日期',
  `storyline` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '剧情简介',
  `year` int NULL DEFAULT NULL COMMENT '年份',
  `writers` json NULL COMMENT '编剧列表，有NAME和ID两个字段',
  `rating_weights` json NULL COMMENT '豆瓣的评分分布，格式是星数和对应的占比',
  `full_search_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci GENERATED ALWAYS AS (concat_ws(_utf8mb4' ',`name`,ifnull(`alias`,_utf8mb4''),ifnull(`storyline`,_utf8mb4''),json_unquote(json_extract(`directors`,_utf8mb4'$[*].name')),json_unquote(json_extract(`actors`,_utf8mb4'$[*].name')),json_unquote(json_extract(`writers`,_utf8mb4'$[*].name')))) STORED NULL,
  PRIMARY KEY (`movie_id`) USING BTREE,
  INDEX `idx_year`(`year` ASC) USING BTREE,
  INDEX `idx_score`(`douban_score` ASC) USING BTREE,
  INDEX `idx_votes`(`douban_votes` ASC) USING BTREE,
  INDEX `idx_score_votes`(`douban_score` DESC, `douban_votes` DESC) USING BTREE,
  INDEX `idx_year_score`(`year` DESC, `douban_score` DESC) USING BTREE,
  INDEX `idx_votes_score`(`douban_votes` DESC, `douban_score` DESC) USING BTREE,
  FULLTEXT INDEX `idx_global_search`(`full_search_text`) WITH PARSER `ngram`
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for persons
-- ----------------------------
DROP TABLE IF EXISTS `persons`;
CREATE TABLE `persons`  (
  `person_id` bigint NOT NULL COMMENT '影人唯一ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '姓名',
  `sex` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '性别',
  `name_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '英文名',
  `name_zh` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '中文名/原名',
  `birth` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生日',
  `birthplace` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '出生地',
  `profession` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '职业',
  `biography` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '个人简介',
  `person_avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像URL',
  PRIMARY KEY (`person_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ratings
-- ----------------------------
DROP TABLE IF EXISTS `ratings`;
CREATE TABLE `ratings`  (
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `rating` int NULL DEFAULT NULL COMMENT '分值(1-5)',
  `rating_time` datetime NULL DEFAULT NULL COMMENT '评分时间',
  PRIMARY KEY (`user_id`, `movie_id`) USING BTREE,
  INDEX `idx_movie_id`(`movie_id` ASC) USING BTREE,
  INDEX `idx_movie_id_rating`(`movie_id` ASC, `rating` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for regions
-- ----------------------------
DROP TABLE IF EXISTS `regions`;
CREATE TABLE `regions`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '地区ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '地区名称',
  `name_en` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '英文名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地区描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影地区表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for stats_hidden_gems
-- ----------------------------
DROP TABLE IF EXISTS `stats_hidden_gems`;
CREATE TABLE `stats_hidden_gems`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上榜理由(如: 9.0分但在本站仅100人看过)',
  `calc_date` date NOT NULL COMMENT '计算/上榜日期(用于区分周次)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_date`(`calc_date` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '冷门佳作推荐榜(每周更新)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stats_hot_movies
-- ----------------------------
DROP TABLE IF EXISTS `stats_hot_movies`;
CREATE TABLE `stats_hot_movies`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID (关联 movie 表)',
  `period_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '统计周期: DAILY(今日), WEEKLY(本周), MONTHLY(本月)',
  `hot_score` double NOT NULL DEFAULT 0 COMMENT '热度分值 (加权计算后的结果)',
  `calc_date` date NOT NULL COMMENT '计算日期 (例如 2023-11-11)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_movie_period_date`(`movie_id` ASC, `period_type` ASC, `calc_date` ASC) USING BTREE,
  INDEX `idx_period_score`(`period_type` ASC, `hot_score` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影热度统计表(Spark离线计算结果)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stats_movie_analysis
-- ----------------------------
DROP TABLE IF EXISTS `stats_movie_analysis`;
CREATE TABLE `stats_movie_analysis`  (
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `sentiment_score` decimal(3, 2) NULL DEFAULT NULL COMMENT '情感综合评分(0.00-1.00)',
  `positive_rate` decimal(5, 2) NULL DEFAULT NULL COMMENT '好评率(百分比)',
  `keywords` json NULL COMMENT '高频关键词(JSON格式,如 [\"剧情反转\",\"特效炸裂\"])',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`movie_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '电影情感分析与关键词统计表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stats_similar_movies
-- ----------------------------
DROP TABLE IF EXISTS `stats_similar_movies`;
CREATE TABLE `stats_similar_movies`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `movie_id` bigint NOT NULL COMMENT '基准电影ID',
  `similar_movie_id` bigint NOT NULL COMMENT '相似电影ID',
  `similarity_score` double NOT NULL COMMENT '相似度分值',
  `similarity_type` tinyint NULL DEFAULT 1 COMMENT '类型: 1-内容相似(标签/演员), 2-协同过滤相似(Item-based)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_movie_pair`(`movie_id` ASC, `similar_movie_id` ASC, `similarity_type` ASC) USING BTREE,
  INDEX `idx_movie_score`(`movie_id` ASC, `similarity_score` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '电影相似度关联表(用于详情页推荐)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stats_user_recs
-- ----------------------------
DROP TABLE IF EXISTS `stats_user_recs`;
CREATE TABLE `stats_user_recs`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `movie_id` bigint NOT NULL COMMENT '推荐电影ID',
  `score` double NOT NULL DEFAULT 0 COMMENT '推荐匹配度/预测评分',
  `algorithm_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ALS' COMMENT '算法类型: ALS, UserCF, ItemCF',
  `calc_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_movie`(`user_id` ASC, `movie_id` ASC) USING BTREE,
  INDEX `idx_user_score`(`user_id` ASC, `score` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户个性化推荐结果表(离线计算)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户唯一标识',
  `user_nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `user_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '$2a$10$lPHc.uX1uT4Q/54HYO9DfO8B4TCOJYAZGsaemn0pLxA3OoHQeOd5S' COMMENT '登录密码(初始默认)',
  `user_avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户头像',
  `user_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '豆瓣个人主页URL',
  `role` tinyint NULL DEFAULT 1 COMMENT '角色: 0管理员, 1普通用户',
  `status` int NULL DEFAULT 0 COMMENT '账号状态 (0:正常, 1:被禁用/冻结, 2:注销)',
  `password_version` int NULL DEFAULT 1 COMMENT '密码版本号，用于失效旧 Token（修改密码后递增）',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户邮箱',
  `create_time` timestamp NULL DEFAULT NULL COMMENT '用户创建时间',
  `update_time` timestamp NULL DEFAULT NULL COMMENT '用户修改时间',
  PRIMARY KEY (`user_id`) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_password_version`(`password_version` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for view_history
-- ----------------------------
DROP TABLE IF EXISTS `view_history`;
CREATE TABLE `view_history`  (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `view_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
  PRIMARY KEY (`history_id`) USING BTREE,
  UNIQUE INDEX `uk_user_movie`(`user_id` ASC, `movie_id` ASC) USING BTREE,
  INDEX `idx_user_time`(`user_id` ASC, `view_time` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;


SET FOREIGN_KEY_CHECKS = 1;
