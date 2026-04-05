-- SQL for ADS stats tables synced from Hive to PostgreSQL
-- These tables store analytical results from Spark jobs

-- ----------------------------
-- Sequences (must be created before tables that reference them)
-- ----------------------------
DROP SEQUENCE IF EXISTS "stats_search_funnel_1d_id_seq";
CREATE SEQUENCE "stats_search_funnel_1d_id_seq" INCREMENT 1 START 1;

DROP SEQUENCE IF EXISTS "stats_search_keyword_insights_1d_id_seq";
CREATE SEQUENCE "stats_search_keyword_insights_1d_id_seq" INCREMENT 1 START 1;

DROP SEQUENCE IF EXISTS "stats_user_funnel_1d_id_seq";
CREATE SEQUENCE "stats_user_funnel_1d_id_seq" INCREMENT 1 START 1;

DROP SEQUENCE IF EXISTS "stats_user_retention_id_seq";
CREATE SEQUENCE "stats_user_retention_id_seq" INCREMENT 1 START 1;

-- ----------------------------
-- Table structure for stats_search_funnel_1d
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_search_funnel_1d";
CREATE TABLE "public"."stats_search_funnel_1d" (
  "id" int8 NOT NULL DEFAULT nextval('stats_search_funnel_1d_id_seq'::regclass),
  "search_user_cnt" int8 NOT NULL DEFAULT 0,
  "search_cnt" int8 NOT NULL DEFAULT 0,
  "search_with_result_cnt" int8 NOT NULL DEFAULT 0,
  "search_zero_result_cnt" int8 NOT NULL DEFAULT 0,
  "after_search_view_user_cnt" int8 NOT NULL DEFAULT 0,
  "after_search_rating_user_cnt" int8 NOT NULL DEFAULT 0,
  "after_search_favorite_user_cnt" int8 NOT NULL DEFAULT 0,
  "after_search_watched_user_cnt" int8 NOT NULL DEFAULT 0,
  "search_to_view_rate" numeric(10,4),
  "search_to_watched_rate" numeric(10,4),
  "search_to_rating_rate" numeric(10,4),
  "calc_date" date NOT NULL
);
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."search_user_cnt" IS '搜索用户数';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."search_cnt" IS '搜索总次数';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."search_with_result_cnt" IS '有结果的搜索次数';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."search_zero_result_cnt" IS '零结果搜索次数';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."after_search_view_user_cnt" IS '搜索后浏览详情的用户数';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."after_search_rating_user_cnt" IS '搜索后评分的用户数';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."after_search_favorite_user_cnt" IS '搜索后收藏的用户数';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."after_search_watched_user_cnt" IS '搜索后标记看过的用户数';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."search_to_view_rate" IS '搜索到浏览转化率';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."search_to_watched_rate" IS '搜索到看过转化率';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."search_to_rating_rate" IS '搜索到评分转化率';
COMMENT ON COLUMN "public"."stats_search_funnel_1d"."calc_date" IS '计算日期';
COMMENT ON TABLE "public"."stats_search_funnel_1d" IS '搜索漏斗分析统计表(每日更新)';

-- ----------------------------
-- Table structure for stats_search_keyword_insights_1d
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_search_keyword_insights_1d";
CREATE TABLE "public"."stats_search_keyword_insights_1d" (
  "id" int8 NOT NULL DEFAULT nextval('stats_search_keyword_insights_1d_id_seq'::regclass),
  "search_keyword" varchar(500) COLLATE "pg_catalog"."default" NOT NULL,
  "rank_no" int4 NOT NULL,
  "search_cnt" int8 NOT NULL DEFAULT 0,
  "search_user_cnt" int8 NOT NULL DEFAULT 0,
  "zero_result_cnt" int8 NOT NULL DEFAULT 0,
  "zero_result_rate" numeric(10,4),
  "avg_result_count" numeric(10,2),
  "after_search_view_user_cnt" int8 NOT NULL DEFAULT 0,
  "after_search_watch_user_cnt" int8 NOT NULL DEFAULT 0,
  "after_search_rating_user_cnt" int8 NOT NULL DEFAULT 0,
  "search_to_view_rate" numeric(10,4),
  "view_to_watch_rate" numeric(10,4),
  "problem_score" numeric(10,4),
  "calc_date" date NOT NULL
);
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."search_keyword" IS '搜索关键词';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."rank_no" IS '排名(按问题分数降序)';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."search_cnt" IS '搜索次数';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."search_user_cnt" IS '搜索用户数';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."zero_result_cnt" IS '零结果次数';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."zero_result_rate" IS '零结果率';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."avg_result_count" IS '平均结果数';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."after_search_view_user_cnt" IS '搜索后浏览用户数';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."after_search_watch_user_cnt" IS '搜索后观看用户数';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."after_search_rating_user_cnt" IS '搜索后评分用户数';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."search_to_view_rate" IS '搜索到浏览转化率';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."view_to_watch_rate" IS '浏览到观看转化率';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."problem_score" IS '问题分数(零结果率和低转化加权)';
COMMENT ON COLUMN "public"."stats_search_keyword_insights_1d"."calc_date" IS '计算日期';
COMMENT ON TABLE "public"."stats_search_keyword_insights_1d" IS '搜索关键词洞察统计表(每日更新)';

-- ----------------------------
-- Table structure for stats_user_funnel_1d
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_user_funnel_1d";
CREATE TABLE "public"."stats_user_funnel_1d" (
  "id" int8 NOT NULL DEFAULT nextval('stats_user_funnel_1d_id_seq'::regclass),
  "total_active_users" int8 NOT NULL DEFAULT 0,
  "view_users" int8 NOT NULL DEFAULT 0,
  "watched_users" int8 NOT NULL DEFAULT 0,
  "rating_users" int8 NOT NULL DEFAULT 0,
  "comment_users" int8 NOT NULL DEFAULT 0,
  "favorite_users" int8 NOT NULL DEFAULT 0,
  "favorite_folder_action_users" int8 NOT NULL DEFAULT 0,
  "view_to_watched_rate" numeric(10,4),
  "watched_to_rating_rate" numeric(10,4),
  "rating_to_comment_rate" numeric(10,4),
  "comment_to_favorite_rate" numeric(10,4),
  "calc_date" date NOT NULL
);
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."total_active_users" IS '活跃用户总数';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."view_users" IS '浏览用户数';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."watched_users" IS '看过用户数';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."rating_users" IS '评分用户数';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."comment_users" IS '评论用户数';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."favorite_users" IS '收藏用户数';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."favorite_folder_action_users" IS '收藏夹操作用户数';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."view_to_watched_rate" IS '浏览到看过转化率';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."watched_to_rating_rate" IS '看过到评分转化率';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."rating_to_comment_rate" IS '评分到评论转化率';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."comment_to_favorite_rate" IS '评论到收藏转化率';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."calc_date" IS '计算日期';
COMMENT ON TABLE "public"."stats_user_funnel_1d" IS '用户漏斗分析统计表(每日更新)';

-- ----------------------------
-- Table structure for stats_user_retention
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_user_retention";
CREATE TABLE "public"."stats_user_retention" (
  "id" int8 NOT NULL DEFAULT nextval('stats_user_retention_id_seq'::regclass),
  "cohort_dt" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
  "retention_day" int4 NOT NULL,
  "cohort_users" int8 NOT NULL DEFAULT 0,
  "retained_users" int8 NOT NULL DEFAULT 0,
  "retention_rate" numeric(10,4),
  "calc_date" date NOT NULL
);
COMMENT ON COLUMN "public"."stats_user_retention"."cohort_dt" IS '用户注册日期(群组日期)';
COMMENT ON COLUMN "public"."stats_user_retention"."retention_day" IS '留存天数(1/7/30等)';
COMMENT ON COLUMN "public"."stats_user_retention"."cohort_users" IS '群组用户总数';
COMMENT ON COLUMN "public"."stats_user_retention"."retained_users" IS '留存用户数';
COMMENT ON COLUMN "public"."stats_user_retention"."retention_rate" IS '留存率';
COMMENT ON COLUMN "public"."stats_user_retention"."calc_date" IS '计算日期';
COMMENT ON TABLE "public"."stats_user_retention" IS '用户留存分析统计表(每日更新)';

-- ----------------------------
-- Sequence structure for stats_genre_preference_1d_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "stats_genre_preference_1d_id_seq";
CREATE SEQUENCE "stats_genre_preference_1d_id_seq" INCREMENT 1 START 1;

-- ----------------------------
-- Table structure for stats_genre_preference_1d
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_genre_preference_1d";
CREATE TABLE "public"."stats_genre_preference_1d" (
  "id" int8 NOT NULL DEFAULT nextval('stats_genre_preference_1d_id_seq'::regclass),
  "genre" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "rank_no" int4 NOT NULL,
  "movie_cnt" int8 NOT NULL DEFAULT 0,
  "view_pv" int8 NOT NULL DEFAULT 0,
  "view_uv" int8 NOT NULL DEFAULT 0,
  "rating_cnt" int8 NOT NULL DEFAULT 0,
  "watched_cnt" int8 NOT NULL DEFAULT 0,
  "hot_score_sum" numeric(18,4),
  "calc_date" date NOT NULL
);
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."genre" IS '类型名称';
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."rank_no" IS '排名(按热度分数降序)';
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."movie_cnt" IS '该类型电影数量';
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."view_pv" IS '总浏览量';
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."view_uv" IS '独立访客数';
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."rating_cnt" IS '评分总数';
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."watched_cnt" IS '标记看过总数';
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."hot_score_sum" IS '热度分数总和';
COMMENT ON COLUMN "public"."stats_genre_preference_1d"."calc_date" IS '计算日期';
COMMENT ON TABLE "public"."stats_genre_preference_1d" IS '类型偏好分析统计表(每日更新)';

-- ----------------------------
-- Indexes for better query performance
-- ----------------------------
CREATE INDEX IF NOT EXISTS idx_stats_search_funnel_calc_date ON "public"."stats_search_funnel_1d" ("calc_date");
CREATE INDEX IF NOT EXISTS idx_stats_search_keyword_insights_calc_date ON "public"."stats_search_keyword_insights_1d" ("calc_date");
CREATE INDEX IF NOT EXISTS idx_stats_search_keyword_insights_rank ON "public"."stats_search_keyword_insights_1d" ("calc_date", "rank_no");
CREATE INDEX IF NOT EXISTS idx_stats_user_funnel_calc_date ON "public"."stats_user_funnel_1d" ("calc_date");
CREATE INDEX IF NOT EXISTS idx_stats_user_retention_cohort ON "public"."stats_user_retention" ("cohort_dt", "retention_day");
CREATE INDEX IF NOT EXISTS idx_stats_user_retention_calc_date ON "public"."stats_user_retention" ("calc_date");
CREATE INDEX IF NOT EXISTS idx_stats_genre_preference_calc_date ON "public"."stats_genre_preference_1d" ("calc_date");
CREATE INDEX IF NOT EXISTS idx_stats_genre_preference_rank ON "public"."stats_genre_preference_1d" ("calc_date", "rank_no");

-- ----------------------------
-- Sequence structure for stats_user_behavior_sankey_1d_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "stats_user_behavior_sankey_1d_id_seq";
CREATE SEQUENCE "stats_user_behavior_sankey_1d_id_seq" INCREMENT 1 START 1;

-- ----------------------------
-- Table structure for stats_user_behavior_sankey_1d
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_user_behavior_sankey_1d";
CREATE TABLE "public"."stats_user_behavior_sankey_1d" (
  "id" int8 NOT NULL DEFAULT nextval('stats_user_behavior_sankey_1d_id_seq'::regclass),
  "source_node" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "target_node" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "user_count" int8 NOT NULL DEFAULT 0,
  "calc_date" date NOT NULL
);
COMMENT ON COLUMN "public"."stats_user_behavior_sankey_1d"."source_node" IS '桑基图源节点名称';
COMMENT ON COLUMN "public"."stats_user_behavior_sankey_1d"."target_node" IS '桑基图目标节点名称';
COMMENT ON COLUMN "public"."stats_user_behavior_sankey_1d"."user_count" IS '从源节点流向目标节点的用户数';
COMMENT ON COLUMN "public"."stats_user_behavior_sankey_1d"."calc_date" IS '计算日期';
COMMENT ON TABLE "public"."stats_user_behavior_sankey_1d" IS '用户行为桑基图统计表(每日更新)';

CREATE INDEX IF NOT EXISTS idx_stats_user_behavior_sankey_calc_date ON "public"."stats_user_behavior_sankey_1d" ("calc_date");
