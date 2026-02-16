-- ========================================
-- favorites 表 Schema 修复脚本
-- 允许同一电影存在于多个收藏夹中
-- 执行时间：2026-02-07
-- ========================================

-- 问题描述：
-- 当前 PRIMARY KEY 是 (user_id, movie_id)，导致一个用户对同一电影只能有一条收藏记录
-- 这使得无法将同一电影添加到多个收藏夹中

-- 解决方案：
-- 将 PRIMARY KEY 改为 (user_id, movie_id, folder_id)
-- 这样同一用户可以将同一电影收藏到不同的收藏夹中

-- 1. 备份原表数据
CREATE TABLE favorites_backup_20260207 AS SELECT * FROM favorites;

-- 2. 删除原表
DROP TABLE IF EXISTS favorites;

-- 3. 创建新表结构（使用三元组主键）
CREATE TABLE `favorites` (
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `folder_id` bigint NOT NULL DEFAULT 0 COMMENT '收藏夹ID，0表示默认收藏夹',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`user_id`, `movie_id`, `folder_id`) USING BTREE,
  INDEX `idx_user_id` (`user_id`) USING BTREE,
  INDEX `idx_folder_id` (`folder_id`) USING BTREE,
  INDEX `idx_movie_id` (`movie_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户收藏记录' ROW_FORMAT = Dynamic;

-- 4. 从备份表恢复数据（将 NULL folder_id 转换为 0）
INSERT INTO favorites (user_id, movie_id, folder_id, create_time)
SELECT user_id, movie_id, COALESCE(folder_id, 0), create_time 
FROM favorites_backup_20260207;

-- 5. 验证数据迁移
SELECT 
    '原表记录数' as description, 
    COUNT(*) as count 
FROM favorites_backup_20260207
UNION ALL
SELECT 
    '新表记录数' as description, 
    COUNT(*) as count 
FROM favorites;

-- 6. 确认无误后，可删除备份表（可选，建议保留一段时间）
-- DROP TABLE favorites_backup_20260207;

-- ========================================
-- 注意事项：
-- 1. 执行前请确保已备份数据库
-- 2. 新的主键使用 (user_id, movie_id, folder_id)，允许同一电影在多个收藏夹中
-- 3. folder_id 改为 NOT NULL DEFAULT 0，0代表默认收藏夹（原来的 NULL）
-- 4. 需要同步修改后端代码中的 NULL 为 0
-- 5. 确认迁移成功后再删除 favorites_backup_20260207 表
-- ========================================
