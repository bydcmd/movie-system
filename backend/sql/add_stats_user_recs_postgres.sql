-- 创建用户个性化推荐结果表 (PostgreSQL 版本)
-- 用于存储离线计算的个性化推荐结果

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
-- Table structure for stats_user_recs
-- ----------------------------
DROP TABLE IF EXISTS "public"."stats_user_recs";
CREATE TABLE "public"."stats_user_recs" (
  "id" int8 NOT NULL DEFAULT nextval('stats_user_recs_id_seq'::regclass),
  "user_id" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "movie_id" int8 NOT NULL,
  "score" float8 NOT NULL DEFAULT 0,
  "algorithm_type" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'ALS',
  "calc_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- Primary Key & Indexes
-- ----------------------------
ALTER TABLE "public"."stats_user_recs" ADD CONSTRAINT "pk_stats_user_recs" PRIMARY KEY ("id");
ALTER TABLE "public"."stats_user_recs" ADD CONSTRAINT "uk_user_movie" UNIQUE ("user_id", "movie_id");
CREATE INDEX "idx_user_score" ON "public"."stats_user_recs" ("user_id", "score" DESC);

-- ----------------------------
-- Comments
-- ----------------------------
COMMENT ON COLUMN "public"."stats_user_recs"."id" IS '主键ID';
COMMENT ON COLUMN "public"."stats_user_recs"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."stats_user_recs"."movie_id" IS '推荐电影ID';
COMMENT ON COLUMN "public"."stats_user_recs"."score" IS '推荐匹配度/预测评分';
COMMENT ON COLUMN "public"."stats_user_recs"."algorithm_type" IS '算法类型: ALS, UserCF, ItemCF';
COMMENT ON COLUMN "public"."stats_user_recs"."calc_time" IS '计算时间';
COMMENT ON TABLE "public"."stats_user_recs" IS '用户个性化推荐结果表(离线计算)';
