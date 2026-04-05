/*
 Navicat Premium Dump SQL

 Source Server         : postgresql
 Source Server Type    : PostgreSQL
 Source Server Version : 170007 (170007)
 Source Host           : localhost:5432
 Source Catalog        : movie_db
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 170007 (170007)
 File Encoding         : 65001

 Date: 04/04/2026 22:51:28
*/


-- ----------------------------
-- Type structure for gtrgm
-- ----------------------------
DROP TYPE IF EXISTS "public"."gtrgm";
CREATE TYPE "public"."gtrgm" (
  INPUT = "public"."gtrgm_in",
  OUTPUT = "public"."gtrgm_out",
  INTERNALLENGTH = VARIABLE,
  CATEGORY = U,
  DELIMITER = ','
);

-- ----------------------------
-- Sequence structure for comment_likes_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."comment_likes_id_seq";
CREATE SEQUENCE "public"."comment_likes_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for comments_comment_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."comments_comment_id_seq";
CREATE SEQUENCE "public"."comments_comment_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for event_outbox_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."event_outbox_id_seq";
CREATE SEQUENCE "public"."event_outbox_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for favorite_folders_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."favorite_folders_id_seq";
CREATE SEQUENCE "public"."favorite_folders_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for genres_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."genres_id_seq";
CREATE SEQUENCE "public"."genres_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for languages_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."languages_id_seq";
CREATE SEQUENCE "public"."languages_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for movie_genre_relation_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."movie_genre_relation_id_seq";
CREATE SEQUENCE "public"."movie_genre_relation_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for movie_language_relation_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."movie_language_relation_id_seq";
CREATE SEQUENCE "public"."movie_language_relation_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for movie_region_relation_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."movie_region_relation_id_seq";
CREATE SEQUENCE "public"."movie_region_relation_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for movies_movie_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."movies_movie_id_seq";
CREATE SEQUENCE "public"."movies_movie_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for regions_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."regions_id_seq";
CREATE SEQUENCE "public"."regions_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for stats_genre_preference_1d_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."stats_genre_preference_1d_id_seq";
CREATE SEQUENCE "public"."stats_genre_preference_1d_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for stats_hot_movies_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."stats_hot_movies_id_seq";
CREATE SEQUENCE "public"."stats_hot_movies_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for stats_search_funnel_1d_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."stats_search_funnel_1d_id_seq";
CREATE SEQUENCE "public"."stats_search_funnel_1d_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for stats_search_keyword_insights_1d_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."stats_search_keyword_insights_1d_id_seq";
CREATE SEQUENCE "public"."stats_search_keyword_insights_1d_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for stats_similar_movies_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."stats_similar_movies_id_seq";
CREATE SEQUENCE "public"."stats_similar_movies_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for stats_user_funnel_1d_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."stats_user_funnel_1d_id_seq";
CREATE SEQUENCE "public"."stats_user_funnel_1d_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for stats_user_recs_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."stats_user_recs_id_seq";
CREATE SEQUENCE "public"."stats_user_recs_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for stats_user_retention_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."stats_user_retention_id_seq";
CREATE SEQUENCE "public"."stats_user_retention_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for view_history_history_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."view_history_history_id_seq";
CREATE SEQUENCE "public"."view_history_history_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Table structure for comment_likes
-- ----------------------------
DROP TABLE IF EXISTS "public"."comment_likes";
CREATE TABLE "public"."comment_likes" (
  "id" int8 NOT NULL DEFAULT nextval('comment_likes_id_seq'::regclass),
  "comment_id" int8 NOT NULL,
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."comment_likes"."comment_id" IS '评论ID';
COMMENT ON COLUMN "public"."comment_likes"."user_id" IS '用户ID';
COMMENT ON TABLE "public"."comment_likes" IS '评论点赞表';

-- ----------------------------
-- Table structure for comments
-- ----------------------------
DROP TABLE IF EXISTS "public"."comments";
CREATE TABLE "public"."comments" (
  "comment_id" int8 NOT NULL DEFAULT nextval('comments_comment_id_seq'::regclass),
  "user_id" varchar(100) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "movie_id" int8,
  "content" text COLLATE "pg_catalog"."default",
  "votes" int4 DEFAULT 0,
  "comment_time" timestamp(6),
  "title" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "type" int2 DEFAULT 1,
  "version" int4 DEFAULT 0,
  "status" int2 DEFAULT 1
)
;
COMMENT ON COLUMN "public"."comments"."comment_id" IS '评论唯一ID（自增）';
COMMENT ON COLUMN "public"."comments"."user_id" IS '关联用户ID';
COMMENT ON COLUMN "public"."comments"."movie_id" IS '关联电影ID';
COMMENT ON COLUMN "public"."comments"."content" IS '评论内容，短评为纯文本，长评是Tiptap的JSON格式';
COMMENT ON COLUMN "public"."comments"."votes" IS '有用点赞数';
COMMENT ON COLUMN "public"."comments"."comment_time" IS '评论时间';
COMMENT ON COLUMN "public"."comments"."title" IS '评论标题(长评专用)';
COMMENT ON COLUMN "public"."comments"."type" IS '评论类型: 1-短评, 2-长评';
COMMENT ON COLUMN "public"."comments"."version" IS '乐观锁版本号';
COMMENT ON COLUMN "public"."comments"."status" IS '评论状态: 1-草稿, 2-发布';
COMMENT ON TABLE "public"."comments" IS '评论表';

-- ----------------------------
-- Table structure for event_outbox
-- ----------------------------
DROP TABLE IF EXISTS "public"."event_outbox";
CREATE TABLE "public"."event_outbox" (
  "id" int8 NOT NULL DEFAULT nextval('event_outbox_id_seq'::regclass),
  "topic" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "message_key" varchar(255) COLLATE "pg_catalog"."default",
  "payload" text COLLATE "pg_catalog"."default" NOT NULL,
  "status" int2 NOT NULL DEFAULT 0,
  "retry_count" int4 NOT NULL DEFAULT 0,
  "next_retry_time" timestamp(6) NOT NULL,
  "created_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "last_error" varchar(1000) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Table structure for favorite_folders
-- ----------------------------
DROP TABLE IF EXISTS "public"."favorite_folders";
CREATE TABLE "public"."favorite_folders" (
  "id" int8 NOT NULL DEFAULT nextval('favorite_folders_id_seq'::regclass),
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(500) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "is_public" int2 DEFAULT 0,
  "movie_count" int4 DEFAULT 0,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "is_default" int4 DEFAULT 0
)
;
COMMENT ON COLUMN "public"."favorite_folders"."id" IS '收藏夹ID';
COMMENT ON COLUMN "public"."favorite_folders"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."favorite_folders"."name" IS '收藏夹名称';
COMMENT ON COLUMN "public"."favorite_folders"."description" IS '收藏夹描述';
COMMENT ON COLUMN "public"."favorite_folders"."is_public" IS '是否公开：0-私密, 1-公开';
COMMENT ON COLUMN "public"."favorite_folders"."movie_count" IS '电影数量';
COMMENT ON COLUMN "public"."favorite_folders"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."favorite_folders"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."favorite_folders"."is_default" IS '是否为默认收藏夹：0-否, 1-是';
COMMENT ON TABLE "public"."favorite_folders" IS '用户自定义收藏夹';

-- ----------------------------
-- Table structure for favorites
-- ----------------------------
DROP TABLE IF EXISTS "public"."favorites";
CREATE TABLE "public"."favorites" (
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "movie_id" int8 NOT NULL,
  "folder_id" int8 NOT NULL DEFAULT 0,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."favorites"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."favorites"."movie_id" IS '电影ID';
COMMENT ON COLUMN "public"."favorites"."folder_id" IS '收藏夹ID，0表示默认收藏夹';
COMMENT ON COLUMN "public"."favorites"."create_time" IS '收藏时间';
COMMENT ON TABLE "public"."favorites" IS '用户收藏记录';

-- ----------------------------
-- Table structure for genres
-- ----------------------------
DROP TABLE IF EXISTS "public"."genres";
CREATE TABLE "public"."genres" (
  "id" int4 NOT NULL DEFAULT nextval('genres_id_seq'::regclass),
  "name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "name_en" varchar(50) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "description" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."genres"."id" IS '类型ID';
COMMENT ON COLUMN "public"."genres"."name" IS '类型名称';
COMMENT ON COLUMN "public"."genres"."name_en" IS '英文名称';
COMMENT ON COLUMN "public"."genres"."description" IS '类型描述';
COMMENT ON COLUMN "public"."genres"."create_time" IS '创建时间';
COMMENT ON TABLE "public"."genres" IS '电影类型表';

-- ----------------------------
-- Table structure for languages
-- ----------------------------
DROP TABLE IF EXISTS "public"."languages";
CREATE TABLE "public"."languages" (
  "id" int4 NOT NULL DEFAULT nextval('languages_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "name_en" varchar(100) COLLATE "pg_catalog"."default",
  "description" varchar(255) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;

-- ----------------------------
-- Table structure for movie_genre_relation
-- ----------------------------
DROP TABLE IF EXISTS "public"."movie_genre_relation";
CREATE TABLE "public"."movie_genre_relation" (
  "id" int8 NOT NULL DEFAULT nextval('movie_genre_relation_id_seq'::regclass),
  "movie_id" int8 NOT NULL,
  "genre_id" int4 NOT NULL,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."movie_genre_relation"."id" IS '关系ID';
COMMENT ON COLUMN "public"."movie_genre_relation"."movie_id" IS '电影ID';
COMMENT ON COLUMN "public"."movie_genre_relation"."genre_id" IS '类型ID';
COMMENT ON COLUMN "public"."movie_genre_relation"."create_time" IS '创建时间';
COMMENT ON TABLE "public"."movie_genre_relation" IS '电影-类型关联表';

-- ----------------------------
-- Table structure for movie_language_relation
-- ----------------------------
DROP TABLE IF EXISTS "public"."movie_language_relation";
CREATE TABLE "public"."movie_language_relation" (
  "id" int8 NOT NULL DEFAULT nextval('movie_language_relation_id_seq'::regclass),
  "movie_id" int8 NOT NULL,
  "language_id" int4 NOT NULL,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;

-- ----------------------------
-- Table structure for movie_region_relation
-- ----------------------------
DROP TABLE IF EXISTS "public"."movie_region_relation";
CREATE TABLE "public"."movie_region_relation" (
  "id" int8 NOT NULL DEFAULT nextval('movie_region_relation_id_seq'::regclass),
  "movie_id" int8 NOT NULL,
  "region_id" int4 NOT NULL,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."movie_region_relation"."id" IS '关系ID';
COMMENT ON COLUMN "public"."movie_region_relation"."movie_id" IS '电影ID';
COMMENT ON COLUMN "public"."movie_region_relation"."region_id" IS '地区ID';
COMMENT ON COLUMN "public"."movie_region_relation"."create_time" IS '创建时间';
COMMENT ON TABLE "public"."movie_region_relation" IS '电影-地区关联表';

-- ----------------------------
-- Table structure for movies
-- ----------------------------
DROP TABLE IF EXISTS "public"."movies";
CREATE TABLE "public"."movies" (
  "movie_id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "alias" text COLLATE "pg_catalog"."default",
  "actors" jsonb,
  "cover" varchar(500) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "directors" jsonb,
  "douban_score" numeric(3,1) DEFAULT NULL::numeric,
  "score" numeric(3,1) DEFAULT NULL::numeric,
  "douban_votes" int4 DEFAULT 0,
  "votes" int4,
  "genres" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "imdb_id" varchar(50) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "languages" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "mins" varchar(50) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "regions" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "release_date" varchar(100) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "storyline" text COLLATE "pg_catalog"."default",
  "year" int4,
  "writers" jsonb,
  "rating_weights" jsonb,
  "full_search_text" text COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."movies"."movie_id" IS '电影唯一ID';
COMMENT ON COLUMN "public"."movies"."name" IS '电影名称';
COMMENT ON COLUMN "public"."movies"."alias" IS '别名';
COMMENT ON COLUMN "public"."movies"."actors" IS '演员列表，有NAME和ID两个字段';
COMMENT ON COLUMN "public"."movies"."cover" IS '封面图URL';
COMMENT ON COLUMN "public"."movies"."directors" IS '导演列表，有NAME和ID两个字段';
COMMENT ON COLUMN "public"."movies"."douban_score" IS '豆瓣评分';
COMMENT ON COLUMN "public"."movies"."score" IS '本站评分';
COMMENT ON COLUMN "public"."movies"."douban_votes" IS '评分人数';
COMMENT ON COLUMN "public"."movies"."votes" IS '本站评分人数';
COMMENT ON COLUMN "public"."movies"."genres" IS '类型';
COMMENT ON COLUMN "public"."movies"."imdb_id" IS 'IMDB ID';
COMMENT ON COLUMN "public"."movies"."languages" IS '语言';
COMMENT ON COLUMN "public"."movies"."mins" IS '片长';
COMMENT ON COLUMN "public"."movies"."regions" IS '地区';
COMMENT ON COLUMN "public"."movies"."release_date" IS '上映日期';
COMMENT ON COLUMN "public"."movies"."storyline" IS '剧情简介';
COMMENT ON COLUMN "public"."movies"."year" IS '年份';
COMMENT ON COLUMN "public"."movies"."writers" IS '编剧列表，有NAME和ID两个字段';
COMMENT ON COLUMN "public"."movies"."rating_weights" IS '豆瓣的评分分布，格式是星数和对应的占比';
COMMENT ON COLUMN "public"."movies"."full_search_text" IS '全局搜索文本（由触发器自动维护）';
COMMENT ON TABLE "public"."movies" IS '电影主表';

-- ----------------------------
-- Table structure for persons
-- ----------------------------
DROP TABLE IF EXISTS "public"."persons";
CREATE TABLE "public"."persons" (
  "person_id" int8 NOT NULL,
  "name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "sex" varchar(10) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "name_en" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "name_zh" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "birth" varchar(100) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "birthplace" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "profession" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "biography" text COLLATE "pg_catalog"."default",
  "person_avatar" varchar(500) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying
)
;
COMMENT ON COLUMN "public"."persons"."person_id" IS '影人唯一ID';
COMMENT ON COLUMN "public"."persons"."name" IS '姓名';
COMMENT ON COLUMN "public"."persons"."sex" IS '性别';
COMMENT ON COLUMN "public"."persons"."name_en" IS '英文名';
COMMENT ON COLUMN "public"."persons"."name_zh" IS '中文名/原名';
COMMENT ON COLUMN "public"."persons"."birth" IS '生日';
COMMENT ON COLUMN "public"."persons"."birthplace" IS '出生地';
COMMENT ON COLUMN "public"."persons"."profession" IS '职业';
COMMENT ON COLUMN "public"."persons"."biography" IS '个人简介';
COMMENT ON COLUMN "public"."persons"."person_avatar" IS '头像URL';
COMMENT ON TABLE "public"."persons" IS '影人表';

-- ----------------------------
-- Table structure for ratings
-- ----------------------------
DROP TABLE IF EXISTS "public"."ratings";
CREATE TABLE "public"."ratings" (
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "movie_id" int8 NOT NULL,
  "rating" int4,
  "rating_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."ratings"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."ratings"."movie_id" IS '电影ID';
COMMENT ON COLUMN "public"."ratings"."rating" IS '分值(1-5)';
COMMENT ON COLUMN "public"."ratings"."rating_time" IS '评分时间';
COMMENT ON TABLE "public"."ratings" IS '用户评分表';

-- ----------------------------
-- Table structure for regions
-- ----------------------------
DROP TABLE IF EXISTS "public"."regions";
CREATE TABLE "public"."regions" (
  "id" int4 NOT NULL DEFAULT nextval('regions_id_seq'::regclass),
  "name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "name_en" varchar(50) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "description" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."regions"."id" IS '地区ID';
COMMENT ON COLUMN "public"."regions"."name" IS '地区名称';
COMMENT ON COLUMN "public"."regions"."name_en" IS '英文名称';
COMMENT ON COLUMN "public"."regions"."description" IS '地区描述';
COMMENT ON COLUMN "public"."regions"."create_time" IS '创建时间';
COMMENT ON TABLE "public"."regions" IS '电影地区表';

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
)
;
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
-- Table structure for stats_hot_movies
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_hot_movies";
CREATE TABLE "public"."stats_hot_movies" (
  "id" int8 NOT NULL DEFAULT nextval('stats_hot_movies_id_seq'::regclass),
  "movie_id" int8 NOT NULL,
  "period_type" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
  "hot_score" float8 NOT NULL DEFAULT 0,
  "calc_date" date NOT NULL
)
;
COMMENT ON COLUMN "public"."stats_hot_movies"."id" IS '主键ID';
COMMENT ON COLUMN "public"."stats_hot_movies"."movie_id" IS '电影ID (关联 movie 表)';
COMMENT ON COLUMN "public"."stats_hot_movies"."period_type" IS '统计周期: DAILY(今日), WEEKLY(本周), MONTHLY(本月), TOTAL(总榜)';
COMMENT ON COLUMN "public"."stats_hot_movies"."hot_score" IS '热度分值 (加权计算后的结果)';
COMMENT ON COLUMN "public"."stats_hot_movies"."calc_date" IS '计算日期 (例如 2023-11-11)';
COMMENT ON TABLE "public"."stats_hot_movies" IS '电影热度统计表(Spark离线计算结果)';

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
)
;
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
)
;
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
-- Table structure for stats_similar_movies
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_similar_movies";
CREATE TABLE "public"."stats_similar_movies" (
  "id" int8 NOT NULL DEFAULT nextval('stats_similar_movies_id_seq'::regclass),
  "movie_id" int8 NOT NULL,
  "similar_movie_id" int8 NOT NULL,
  "similarity_score" float8 NOT NULL,
  "similarity_type" int2 DEFAULT 1
)
;
COMMENT ON COLUMN "public"."stats_similar_movies"."movie_id" IS '基准电影ID';
COMMENT ON COLUMN "public"."stats_similar_movies"."similar_movie_id" IS '相似电影ID';
COMMENT ON COLUMN "public"."stats_similar_movies"."similarity_score" IS '相似度分值';
COMMENT ON COLUMN "public"."stats_similar_movies"."similarity_type" IS '类型: 1-内容相似(标签/演员), 2-协同过滤相似(Item-based)';
COMMENT ON TABLE "public"."stats_similar_movies" IS '电影相似度关联表(用于详情页推荐)';

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
)
;
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
-- Table structure for stats_user_recs
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_user_recs";
CREATE TABLE "public"."stats_user_recs" (
  "id" int8 NOT NULL DEFAULT nextval('stats_user_recs_id_seq'::regclass),
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "movie_id" int8 NOT NULL,
  "score" float8 NOT NULL DEFAULT 0,
  "algorithm_type" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'ALS'::character varying,
  "calc_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."stats_user_recs"."id" IS '主键ID';
COMMENT ON COLUMN "public"."stats_user_recs"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."stats_user_recs"."movie_id" IS '推荐电影ID';
COMMENT ON COLUMN "public"."stats_user_recs"."score" IS '推荐匹配度/预测评分';
COMMENT ON COLUMN "public"."stats_user_recs"."algorithm_type" IS '算法类型: ALS, UserCF, ItemCF';
COMMENT ON COLUMN "public"."stats_user_recs"."calc_time" IS '计算时间';
COMMENT ON TABLE "public"."stats_user_recs" IS '用户个性化推荐结果表(离线计算)';

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
)
;
COMMENT ON COLUMN "public"."stats_user_retention"."cohort_dt" IS '用户注册日期(群组日期)';
COMMENT ON COLUMN "public"."stats_user_retention"."retention_day" IS '留存天数(1/7/30等)';
COMMENT ON COLUMN "public"."stats_user_retention"."cohort_users" IS '群组用户总数';
COMMENT ON COLUMN "public"."stats_user_retention"."retained_users" IS '留存用户数';
COMMENT ON COLUMN "public"."stats_user_retention"."retention_rate" IS '留存率';
COMMENT ON COLUMN "public"."stats_user_retention"."calc_date" IS '计算日期';
COMMENT ON TABLE "public"."stats_user_retention" IS '用户留存分析统计表(每日更新)';

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS "public"."users";
CREATE TABLE "public"."users" (
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "user_nickname" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "user_password" varchar(255) COLLATE "pg_catalog"."default" DEFAULT '$2a$10$lPHc.uX1uT4Q/54HYO9DfO8B4TCOJYAZGsaemn0pLxA3OoHQeOd5S'::character varying,
  "user_avatar" varchar(500) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "user_url" varchar(500) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "role" int2 DEFAULT 1,
  "status" int4 DEFAULT 0,
  "password_version" int4 DEFAULT 1,
  "email" varchar(255) COLLATE "pg_catalog"."default" DEFAULT NULL::character varying,
  "create_time" timestamp(6),
  "update_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."users"."user_id" IS '用户唯一标识';
COMMENT ON COLUMN "public"."users"."user_nickname" IS '用户昵称';
COMMENT ON COLUMN "public"."users"."user_password" IS '登录密码(初始默认)';
COMMENT ON COLUMN "public"."users"."user_avatar" IS '用户头像';
COMMENT ON COLUMN "public"."users"."user_url" IS '豆瓣个人主页URL';
COMMENT ON COLUMN "public"."users"."role" IS '角色: 0管理员, 1普通用户';
COMMENT ON COLUMN "public"."users"."status" IS '账号状态 (0:正常, 1:被禁用/冻结, 2:注销)';
COMMENT ON COLUMN "public"."users"."password_version" IS '密码版本号，用于失效旧 Token（修改密码后递增）';
COMMENT ON COLUMN "public"."users"."email" IS '用户邮箱';
COMMENT ON COLUMN "public"."users"."create_time" IS '用户创建时间';
COMMENT ON COLUMN "public"."users"."update_time" IS '用户修改时间';
COMMENT ON TABLE "public"."users" IS '用户表';

-- ----------------------------
-- Table structure for view_history
-- ----------------------------
DROP TABLE IF EXISTS "public"."view_history";
CREATE TABLE "public"."view_history" (
  "history_id" int8 NOT NULL DEFAULT nextval('view_history_history_id_seq'::regclass),
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "movie_id" int8 NOT NULL,
  "view_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."view_history"."history_id" IS '历史记录ID';
COMMENT ON COLUMN "public"."view_history"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."view_history"."movie_id" IS '电影ID';
COMMENT ON COLUMN "public"."view_history"."view_time" IS '浏览时间';
COMMENT ON TABLE "public"."view_history" IS '浏览历史表';

-- ----------------------------
-- Table structure for watched_movies
-- ----------------------------
DROP TABLE IF EXISTS "public"."watched_movies";
CREATE TABLE "public"."watched_movies" (
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "movie_id" int8 NOT NULL,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."watched_movies"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."watched_movies"."movie_id" IS '电影ID';
COMMENT ON COLUMN "public"."watched_movies"."create_time" IS '看过标记时间';
COMMENT ON TABLE "public"."watched_movies" IS '用户看过记录';

-- ----------------------------
-- Function structure for gin_extract_query_trgm
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gin_extract_query_trgm"(text, internal, int2, internal, internal, internal, internal);
CREATE FUNCTION "public"."gin_extract_query_trgm"(text, internal, int2, internal, internal, internal, internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gin_extract_query_trgm'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gin_extract_value_trgm
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gin_extract_value_trgm"(text, internal);
CREATE FUNCTION "public"."gin_extract_value_trgm"(text, internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gin_extract_value_trgm'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gin_trgm_consistent
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gin_trgm_consistent"(internal, int2, text, int4, internal, internal, internal, internal);
CREATE FUNCTION "public"."gin_trgm_consistent"(internal, int2, text, int4, internal, internal, internal, internal)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'gin_trgm_consistent'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gin_trgm_triconsistent
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gin_trgm_triconsistent"(internal, int2, text, int4, internal, internal, internal);
CREATE FUNCTION "public"."gin_trgm_triconsistent"(internal, int2, text, int4, internal, internal, internal)
  RETURNS "pg_catalog"."char" AS '$libdir/pg_trgm', 'gin_trgm_triconsistent'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_compress
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_compress"(internal);
CREATE FUNCTION "public"."gtrgm_compress"(internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_compress'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_consistent
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_consistent"(internal, text, int2, oid, internal);
CREATE FUNCTION "public"."gtrgm_consistent"(internal, text, int2, oid, internal)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'gtrgm_consistent'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_decompress
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_decompress"(internal);
CREATE FUNCTION "public"."gtrgm_decompress"(internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_decompress'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_distance
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_distance"(internal, text, int2, oid, internal);
CREATE FUNCTION "public"."gtrgm_distance"(internal, text, int2, oid, internal)
  RETURNS "pg_catalog"."float8" AS '$libdir/pg_trgm', 'gtrgm_distance'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_in
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_in"(cstring);
CREATE FUNCTION "public"."gtrgm_in"(cstring)
  RETURNS "public"."gtrgm" AS '$libdir/pg_trgm', 'gtrgm_in'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_options
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_options"(internal);
CREATE FUNCTION "public"."gtrgm_options"(internal)
  RETURNS "pg_catalog"."void" AS '$libdir/pg_trgm', 'gtrgm_options'
  LANGUAGE c IMMUTABLE
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_out
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_out"("public"."gtrgm");
CREATE FUNCTION "public"."gtrgm_out"("public"."gtrgm")
  RETURNS "pg_catalog"."cstring" AS '$libdir/pg_trgm', 'gtrgm_out'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_penalty
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_penalty"(internal, internal, internal);
CREATE FUNCTION "public"."gtrgm_penalty"(internal, internal, internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_penalty'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_picksplit
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_picksplit"(internal, internal);
CREATE FUNCTION "public"."gtrgm_picksplit"(internal, internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_picksplit'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_same
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_same"("public"."gtrgm", "public"."gtrgm", internal);
CREATE FUNCTION "public"."gtrgm_same"("public"."gtrgm", "public"."gtrgm", internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_same'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_union
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_union"(internal, internal);
CREATE FUNCTION "public"."gtrgm_union"(internal, internal)
  RETURNS "public"."gtrgm" AS '$libdir/pg_trgm', 'gtrgm_union'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for set_limit
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."set_limit"(float4);
CREATE FUNCTION "public"."set_limit"(float4)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'set_limit'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for show_limit
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."show_limit"();
CREATE FUNCTION "public"."show_limit"()
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'show_limit'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for show_trgm
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."show_trgm"(text);
CREATE FUNCTION "public"."show_trgm"(text)
  RETURNS "pg_catalog"."_text" AS '$libdir/pg_trgm', 'show_trgm'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for similarity
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."similarity"(text, text);
CREATE FUNCTION "public"."similarity"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'similarity'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for similarity_dist
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."similarity_dist"(text, text);
CREATE FUNCTION "public"."similarity_dist"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'similarity_dist'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for similarity_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."similarity_op"(text, text);
CREATE FUNCTION "public"."similarity_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'similarity_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity"(text, text);
CREATE FUNCTION "public"."strict_word_similarity"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'strict_word_similarity'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity_commutator_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity_commutator_op"(text, text);
CREATE FUNCTION "public"."strict_word_similarity_commutator_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'strict_word_similarity_commutator_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity_dist_commutator_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity_dist_commutator_op"(text, text);
CREATE FUNCTION "public"."strict_word_similarity_dist_commutator_op"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'strict_word_similarity_dist_commutator_op'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity_dist_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity_dist_op"(text, text);
CREATE FUNCTION "public"."strict_word_similarity_dist_op"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'strict_word_similarity_dist_op'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity_op"(text, text);
CREATE FUNCTION "public"."strict_word_similarity_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'strict_word_similarity_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for update_favorite_folders_update_time
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."update_favorite_folders_update_time"();
CREATE FUNCTION "public"."update_favorite_folders_update_time"()
  RETURNS "pg_catalog"."trigger" AS $BODY$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE
  COST 100;

-- ----------------------------
-- Function structure for update_movies_full_search_text
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."update_movies_full_search_text"();
CREATE FUNCTION "public"."update_movies_full_search_text"()
  RETURNS "pg_catalog"."trigger" AS $BODY$
DECLARE
    -- 定义变量来存储提取出的名字字符串
    v_directors text;
    v_actors text;
    v_writers text;
BEGIN
    -- 1. 提取导演名字 (将 JSONB 数组转为以空格分隔的字符串)
    -- COALESCE 防止 NULL 导致整个字符串变 NULL
    SELECT COALESCE(string_agg(elem->>'name', ' '), '') 
    INTO v_directors
    FROM jsonb_array_elements(NEW.directors) AS elem;

    -- 2. 提取演员名字
    SELECT COALESCE(string_agg(elem->>'name', ' '), '') 
    INTO v_actors
    FROM jsonb_array_elements(NEW.actors) AS elem;

    -- 3. 提取编剧名字
    SELECT COALESCE(string_agg(elem->>'name', ' '), '') 
    INTO v_writers
    FROM jsonb_array_elements(NEW.writers) AS elem;

    -- 4. 拼接所有字段到 full_search_text
    NEW.full_search_text := 
        COALESCE(NEW.name, '') || ' ' || 
        COALESCE(NEW.alias, '') || ' ' || 
        COALESCE(NEW.storyline, '') || ' ' || 
        COALESCE(v_directors, '') || ' ' || 
        COALESCE(v_actors, '') || ' ' || 
        COALESCE(v_writers, '');

    RETURN NEW;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE
  COST 100;

-- ----------------------------
-- Function structure for update_update_time
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."update_update_time"();
CREATE FUNCTION "public"."update_update_time"()
  RETURNS "pg_catalog"."trigger" AS $BODY$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE
  COST 100;

-- ----------------------------
-- Function structure for word_similarity
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity"(text, text);
CREATE FUNCTION "public"."word_similarity"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'word_similarity'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity_commutator_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity_commutator_op"(text, text);
CREATE FUNCTION "public"."word_similarity_commutator_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'word_similarity_commutator_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity_dist_commutator_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity_dist_commutator_op"(text, text);
CREATE FUNCTION "public"."word_similarity_dist_commutator_op"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'word_similarity_dist_commutator_op'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity_dist_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity_dist_op"(text, text);
CREATE FUNCTION "public"."word_similarity_dist_op"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'word_similarity_dist_op'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity_op"(text, text);
CREATE FUNCTION "public"."word_similarity_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'word_similarity_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."comment_likes_id_seq"
OWNED BY "public"."comment_likes"."id";
SELECT setval('"public"."comment_likes_id_seq"', 1152844557602285249, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."comments_comment_id_seq"
OWNED BY "public"."comments"."comment_id";
SELECT setval('"public"."comments_comment_id_seq"', 1152730379458285628, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
SELECT setval('"public"."event_outbox_id_seq"', 83, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."favorite_folders_id_seq"
OWNED BY "public"."favorite_folders"."id";
SELECT setval('"public"."favorite_folders_id_seq"', 1152491045462596108, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."genres_id_seq"
OWNED BY "public"."genres"."id";
SELECT setval('"public"."genres_id_seq"', 30, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."languages_id_seq"
OWNED BY "public"."languages"."id";
SELECT setval('"public"."languages_id_seq"', 403, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."movie_genre_relation_id_seq"
OWNED BY "public"."movie_genre_relation"."id";
SELECT setval('"public"."movie_genre_relation_id_seq"', 3943, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."movie_language_relation_id_seq"
OWNED BY "public"."movie_language_relation"."id";
SELECT setval('"public"."movie_language_relation_id_seq"', 8796, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."movie_region_relation_id_seq"
OWNED BY "public"."movie_region_relation"."id";
SELECT setval('"public"."movie_region_relation_id_seq"', 10866, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."movies_movie_id_seq"
OWNED BY "public"."movies"."movie_id";
SELECT setval('"public"."movies_movie_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."regions_id_seq"
OWNED BY "public"."regions"."id";
SELECT setval('"public"."regions_id_seq"', 355, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
SELECT setval('"public"."stats_genre_preference_1d_id_seq"', 83, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."stats_hot_movies_id_seq"
OWNED BY "public"."stats_hot_movies"."id";
SELECT setval('"public"."stats_hot_movies_id_seq"', 2722, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
SELECT setval('"public"."stats_search_funnel_1d_id_seq"', 6, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
SELECT setval('"public"."stats_search_keyword_insights_1d_id_seq"', 278, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."stats_similar_movies_id_seq"
OWNED BY "public"."stats_similar_movies"."id";
SELECT setval('"public"."stats_similar_movies_id_seq"', 2050411, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
SELECT setval('"public"."stats_user_funnel_1d_id_seq"', 5, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
SELECT setval('"public"."stats_user_recs_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
SELECT setval('"public"."stats_user_retention_id_seq"', 33, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."view_history_history_id_seq"
OWNED BY "public"."view_history"."history_id";
SELECT setval('"public"."view_history_history_id_seq"', 1152751459502767551, true);

-- ----------------------------
-- Indexes structure for table comment_likes
-- ----------------------------
CREATE INDEX "idx_comment_likes_comment_id" ON "public"."comment_likes" USING btree (
  "comment_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_comment_likes_user_id" ON "public"."comment_likes" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table comment_likes
-- ----------------------------
ALTER TABLE "public"."comment_likes" ADD CONSTRAINT "uk_comment_user" UNIQUE ("comment_id", "user_id");

-- ----------------------------
-- Primary Key structure for table comment_likes
-- ----------------------------
ALTER TABLE "public"."comment_likes" ADD CONSTRAINT "comment_likes_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table comments
-- ----------------------------
CREATE INDEX "idx_comments_movie_id" ON "public"."comments" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_comments_user_movie" ON "public"."comments" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_comments_user_movie_type_status" ON "public"."comments" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "type" "pg_catalog"."int2_ops" ASC NULLS LAST,
  "status" "pg_catalog"."int2_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "uk_comments_long_review_draft" ON "public"."comments" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
) WHERE type = 2 AND status = 1;
CREATE UNIQUE INDEX "uk_comments_published_per_type" ON "public"."comments" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "type" "pg_catalog"."int2_ops" ASC NULLS LAST
) WHERE status = 2;

-- ----------------------------
-- Checks structure for table comments
-- ----------------------------
ALTER TABLE "public"."comments" ADD CONSTRAINT "chk_comments_no_short_draft" CHECK (NOT (type = 1 AND status = 1));

-- ----------------------------
-- Primary Key structure for table comments
-- ----------------------------
ALTER TABLE "public"."comments" ADD CONSTRAINT "comments_pkey" PRIMARY KEY ("comment_id");

-- ----------------------------
-- Indexes structure for table event_outbox
-- ----------------------------
CREATE INDEX "idx_event_outbox_status_time" ON "public"."event_outbox" USING btree (
  "status" "pg_catalog"."int2_ops" ASC NULLS LAST,
  "next_retry_time" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table event_outbox
-- ----------------------------
ALTER TABLE "public"."event_outbox" ADD CONSTRAINT "event_outbox_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table favorite_folders
-- ----------------------------
CREATE INDEX "idx_favorite_folders_user_id" ON "public"."favorite_folders" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Triggers structure for table favorite_folders
-- ----------------------------
CREATE TRIGGER "trigger_favorite_folders_update_time" BEFORE UPDATE ON "public"."favorite_folders"
FOR EACH ROW
WHEN ((((old.name)::text IS DISTINCT FROM (new.name)::text) OR ((old.description)::text IS DISTINCT FROM (new.description)::text)))
EXECUTE PROCEDURE "public"."update_update_time"();

-- ----------------------------
-- Primary Key structure for table favorite_folders
-- ----------------------------
ALTER TABLE "public"."favorite_folders" ADD CONSTRAINT "favorite_folders_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table favorites
-- ----------------------------
CREATE INDEX "idx_favorites_folder_id" ON "public"."favorites" USING btree (
  "folder_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_favorites_movie_id" ON "public"."favorites" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_favorites_user_id" ON "public"."favorites" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table favorites
-- ----------------------------
ALTER TABLE "public"."favorites" ADD CONSTRAINT "favorites_pkey" PRIMARY KEY ("user_id", "movie_id", "folder_id");

-- ----------------------------
-- Uniques structure for table genres
-- ----------------------------
ALTER TABLE "public"."genres" ADD CONSTRAINT "uk_genres_name" UNIQUE ("name");

-- ----------------------------
-- Primary Key structure for table genres
-- ----------------------------
ALTER TABLE "public"."genres" ADD CONSTRAINT "genres_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table languages
-- ----------------------------
CREATE UNIQUE INDEX "uk_languages_name" ON "public"."languages" USING btree (
  "name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table languages
-- ----------------------------
ALTER TABLE "public"."languages" ADD CONSTRAINT "languages_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table movie_genre_relation
-- ----------------------------
CREATE INDEX "idx_movie_genre_genre_id" ON "public"."movie_genre_relation" USING btree (
  "genre_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_movie_genre_movie_id" ON "public"."movie_genre_relation" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table movie_genre_relation
-- ----------------------------
ALTER TABLE "public"."movie_genre_relation" ADD CONSTRAINT "uk_movie_genre" UNIQUE ("movie_id", "genre_id");

-- ----------------------------
-- Primary Key structure for table movie_genre_relation
-- ----------------------------
ALTER TABLE "public"."movie_genre_relation" ADD CONSTRAINT "movie_genre_relation_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table movie_language_relation
-- ----------------------------
CREATE INDEX "idx_movie_language_language_id" ON "public"."movie_language_relation" USING btree (
  "language_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_movie_language_movie_id" ON "public"."movie_language_relation" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table movie_language_relation
-- ----------------------------
ALTER TABLE "public"."movie_language_relation" ADD CONSTRAINT "uk_movie_language" UNIQUE ("movie_id", "language_id");

-- ----------------------------
-- Primary Key structure for table movie_language_relation
-- ----------------------------
ALTER TABLE "public"."movie_language_relation" ADD CONSTRAINT "movie_language_relation_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table movie_region_relation
-- ----------------------------
CREATE INDEX "idx_movie_region_movie_id" ON "public"."movie_region_relation" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_movie_region_region_id" ON "public"."movie_region_relation" USING btree (
  "region_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table movie_region_relation
-- ----------------------------
ALTER TABLE "public"."movie_region_relation" ADD CONSTRAINT "uk_movie_region" UNIQUE ("movie_id", "region_id");

-- ----------------------------
-- Primary Key structure for table movie_region_relation
-- ----------------------------
ALTER TABLE "public"."movie_region_relation" ADD CONSTRAINT "movie_region_relation_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table movies
-- ----------------------------
CREATE INDEX "idx_movies_actors_gin" ON "public"."movies" USING gin (
  "actors" "pg_catalog"."jsonb_path_ops"
);
CREATE INDEX "idx_movies_directors_gin" ON "public"."movies" USING gin (
  "directors" "pg_catalog"."jsonb_path_ops"
);
CREATE INDEX "idx_movies_douban_score" ON "public"."movies" USING btree (
  "douban_score" "pg_catalog"."numeric_ops" ASC NULLS LAST
);
CREATE INDEX "idx_movies_douban_votes" ON "public"."movies" USING btree (
  "douban_votes" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_movies_full_search" ON "public"."movies" USING gin (
  "full_search_text" COLLATE "pg_catalog"."default" "public"."gin_trgm_ops"
);
CREATE INDEX "idx_movies_score_votes" ON "public"."movies" USING btree (
  "douban_score" "pg_catalog"."numeric_ops" DESC NULLS FIRST,
  "douban_votes" "pg_catalog"."int4_ops" DESC NULLS FIRST
);
CREATE INDEX "idx_movies_votes_score" ON "public"."movies" USING btree (
  "douban_votes" "pg_catalog"."int4_ops" DESC NULLS FIRST,
  "douban_score" "pg_catalog"."numeric_ops" DESC NULLS FIRST
);
CREATE INDEX "idx_movies_year" ON "public"."movies" USING btree (
  "year" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_movies_year_score" ON "public"."movies" USING btree (
  "year" "pg_catalog"."int4_ops" DESC NULLS FIRST,
  "douban_score" "pg_catalog"."numeric_ops" DESC NULLS FIRST
);

-- ----------------------------
-- Triggers structure for table movies
-- ----------------------------
CREATE TRIGGER "trigger_movies_search_insert" BEFORE INSERT ON "public"."movies"
FOR EACH ROW
EXECUTE PROCEDURE "public"."update_movies_full_search_text"();
CREATE TRIGGER "trigger_movies_search_update" BEFORE UPDATE ON "public"."movies"
FOR EACH ROW
WHEN ((((new.name)::text IS DISTINCT FROM (old.name)::text) OR (new.alias IS DISTINCT FROM old.alias) OR (new.storyline IS DISTINCT FROM old.storyline) OR (new.directors IS DISTINCT FROM old.directors) OR (new.actors IS DISTINCT FROM old.actors) OR (new.writers IS DISTINCT FROM old.writers)))
EXECUTE PROCEDURE "public"."update_movies_full_search_text"();

-- ----------------------------
-- Primary Key structure for table movies
-- ----------------------------
ALTER TABLE "public"."movies" ADD CONSTRAINT "movies_pkey" PRIMARY KEY ("movie_id");

-- ----------------------------
-- Primary Key structure for table persons
-- ----------------------------
ALTER TABLE "public"."persons" ADD CONSTRAINT "persons_pkey" PRIMARY KEY ("person_id");

-- ----------------------------
-- Indexes structure for table ratings
-- ----------------------------
CREATE INDEX "idx_ratings_movie_id" ON "public"."ratings" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_ratings_movie_id_rating" ON "public"."ratings" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "rating" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table ratings
-- ----------------------------
ALTER TABLE "public"."ratings" ADD CONSTRAINT "ratings_pkey" PRIMARY KEY ("user_id", "movie_id");

-- ----------------------------
-- Uniques structure for table regions
-- ----------------------------
ALTER TABLE "public"."regions" ADD CONSTRAINT "uk_regions_name" UNIQUE ("name");

-- ----------------------------
-- Primary Key structure for table regions
-- ----------------------------
ALTER TABLE "public"."regions" ADD CONSTRAINT "regions_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table stats_genre_preference_1d
-- ----------------------------
CREATE INDEX "idx_stats_genre_preference_calc_date" ON "public"."stats_genre_preference_1d" USING btree (
  "calc_date" "pg_catalog"."date_ops" ASC NULLS LAST
);
CREATE INDEX "idx_stats_genre_preference_rank" ON "public"."stats_genre_preference_1d" USING btree (
  "calc_date" "pg_catalog"."date_ops" ASC NULLS LAST,
  "rank_no" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Indexes structure for table stats_hot_movies
-- ----------------------------
CREATE INDEX "idx_hot_movies_period_score" ON "public"."stats_hot_movies" USING btree (
  "period_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "hot_score" "pg_catalog"."float8_ops" DESC NULLS FIRST
);

-- ----------------------------
-- Uniques structure for table stats_hot_movies
-- ----------------------------
ALTER TABLE "public"."stats_hot_movies" ADD CONSTRAINT "uk_movie_period_date" UNIQUE ("movie_id", "period_type", "calc_date");

-- ----------------------------
-- Primary Key structure for table stats_hot_movies
-- ----------------------------
ALTER TABLE "public"."stats_hot_movies" ADD CONSTRAINT "stats_hot_movies_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table stats_search_funnel_1d
-- ----------------------------
CREATE INDEX "idx_stats_search_funnel_calc_date" ON "public"."stats_search_funnel_1d" USING btree (
  "calc_date" "pg_catalog"."date_ops" ASC NULLS LAST
);

-- ----------------------------
-- Indexes structure for table stats_search_keyword_insights_1d
-- ----------------------------
CREATE INDEX "idx_stats_search_keyword_insights_calc_date" ON "public"."stats_search_keyword_insights_1d" USING btree (
  "calc_date" "pg_catalog"."date_ops" ASC NULLS LAST
);
CREATE INDEX "idx_stats_search_keyword_insights_rank" ON "public"."stats_search_keyword_insights_1d" USING btree (
  "calc_date" "pg_catalog"."date_ops" ASC NULLS LAST,
  "rank_no" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Indexes structure for table stats_similar_movies
-- ----------------------------
CREATE INDEX "idx_similar_movies_score" ON "public"."stats_similar_movies" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "similarity_score" "pg_catalog"."float8_ops" DESC NULLS FIRST
);

-- ----------------------------
-- Uniques structure for table stats_similar_movies
-- ----------------------------
ALTER TABLE "public"."stats_similar_movies" ADD CONSTRAINT "uk_movie_pair" UNIQUE ("movie_id", "similar_movie_id", "similarity_type");

-- ----------------------------
-- Primary Key structure for table stats_similar_movies
-- ----------------------------
ALTER TABLE "public"."stats_similar_movies" ADD CONSTRAINT "stats_similar_movies_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table stats_user_funnel_1d
-- ----------------------------
CREATE INDEX "idx_stats_user_funnel_calc_date" ON "public"."stats_user_funnel_1d" USING btree (
  "calc_date" "pg_catalog"."date_ops" ASC NULLS LAST
);

-- ----------------------------
-- Indexes structure for table stats_user_recs
-- ----------------------------
CREATE INDEX "idx_user_score" ON "public"."stats_user_recs" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "score" "pg_catalog"."float8_ops" DESC NULLS FIRST
);

-- ----------------------------
-- Uniques structure for table stats_user_recs
-- ----------------------------
ALTER TABLE "public"."stats_user_recs" ADD CONSTRAINT "uk_user_movie" UNIQUE ("user_id", "movie_id");

-- ----------------------------
-- Primary Key structure for table stats_user_recs
-- ----------------------------
ALTER TABLE "public"."stats_user_recs" ADD CONSTRAINT "pk_stats_user_recs" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table stats_user_retention
-- ----------------------------
CREATE INDEX "idx_stats_user_retention_calc_date" ON "public"."stats_user_retention" USING btree (
  "calc_date" "pg_catalog"."date_ops" ASC NULLS LAST
);
CREATE INDEX "idx_stats_user_retention_cohort" ON "public"."stats_user_retention" USING btree (
  "cohort_dt" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "retention_day" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Indexes structure for table users
-- ----------------------------
CREATE INDEX "idx_users_password_version" ON "public"."users" USING btree (
  "password_version" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_users_status" ON "public"."users" USING btree (
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table users
-- ----------------------------
ALTER TABLE "public"."users" ADD CONSTRAINT "users_pkey" PRIMARY KEY ("user_id");

-- ----------------------------
-- Indexes structure for table view_history
-- ----------------------------
CREATE INDEX "idx_view_history_user_time" ON "public"."view_history" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "view_time" "pg_catalog"."timestamp_ops" DESC NULLS FIRST
);

-- ----------------------------
-- Uniques structure for table view_history
-- ----------------------------
ALTER TABLE "public"."view_history" ADD CONSTRAINT "uk_view_history_user_movie" UNIQUE ("user_id", "movie_id");

-- ----------------------------
-- Primary Key structure for table view_history
-- ----------------------------
ALTER TABLE "public"."view_history" ADD CONSTRAINT "view_history_pkey" PRIMARY KEY ("history_id");

-- ----------------------------
-- Indexes structure for table watched_movies
-- ----------------------------
CREATE INDEX "idx_watched_movie_id" ON "public"."watched_movies" USING btree (
  "movie_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_watched_user_id" ON "public"."watched_movies" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table watched_movies
-- ----------------------------
ALTER TABLE "public"."watched_movies" ADD CONSTRAINT "watched_movies_pkey" PRIMARY KEY ("user_id", "movie_id");

-- ----------------------------
-- Foreign Keys structure for table watched_movies
-- ----------------------------
ALTER TABLE "public"."watched_movies" ADD CONSTRAINT "fk_watched_movies_movie_id" FOREIGN KEY ("movie_id") REFERENCES "public"."movies" ("movie_id") ON DELETE CASCADE ON UPDATE NO ACTION;
