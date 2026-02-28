-- 创建用户“看过”记录表
CREATE TABLE IF NOT EXISTS `watched_movies`  (
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '看过标记时间',
  PRIMARY KEY (`user_id`, `movie_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_movie_id`(`movie_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户看过记录' ROW_FORMAT = DYNAMIC;
