/*
 Database Normalization Script: Genres and Regions
 
 Purpose: Refactor movies table to normalize genres and regions relationships
 - Create separate genres and regions tables
 - Create junction tables for many-to-many relationships
 - Migrate existing data from comma-separated fields to normalized structure
 
 Date: February 8, 2026
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Step 1: Create genres table
-- ----------------------------
DROP TABLE IF EXISTS `genres`;
CREATE TABLE `genres` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '类型ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类型名称',
  `name_en` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '英文名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影类型表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Step 2: Create regions table
-- ----------------------------
DROP TABLE IF EXISTS `regions`;
CREATE TABLE `regions` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '地区ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '地区名称',
  `name_en` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '英文名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地区描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影地区表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Step 3: Create movie_genre_relation table (many-to-many)
-- ----------------------------
DROP TABLE IF EXISTS `movie_genre_relation`;
CREATE TABLE `movie_genre_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `genre_id` int NOT NULL COMMENT '类型ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_movie_genre`(`movie_id` ASC, `genre_id` ASC) USING BTREE,
  INDEX `idx_movie_id`(`movie_id` ASC) USING BTREE,
  INDEX `idx_genre_id`(`genre_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影-类型关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Step 4: Create movie_region_relation table (many-to-many)
-- ----------------------------
DROP TABLE IF EXISTS `movie_region_relation`;
CREATE TABLE `movie_region_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `region_id` int NOT NULL COMMENT '地区ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_movie_region`(`movie_id` ASC, `region_id` ASC) USING BTREE,
  INDEX `idx_movie_id`(`movie_id` ASC) USING BTREE,
  INDEX `idx_region_id`(`region_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电影-地区关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Step 5: Migrate existing genres data
-- ----------------------------

-- Insert unique genres from movies table
INSERT INTO `genres` (`name`)
SELECT DISTINCT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(genres, '/', numbers.n), '/', -1)) AS genre_name
FROM (
  SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) numbers
INNER JOIN movies ON CHAR_LENGTH(genres) - CHAR_LENGTH(REPLACE(genres, '/', '')) >= numbers.n - 1
WHERE genres IS NOT NULL AND genres != ''
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(genres, '/', numbers.n), '/', -1)) != ''
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Populate movie_genre_relation table
INSERT INTO `movie_genre_relation` (`movie_id`, `genre_id`)
SELECT DISTINCT m.movie_id, g.id
FROM movies m
INNER JOIN (
  SELECT movie_id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(genres, '/', numbers.n), '/', -1)) AS genre_name
  FROM (
    SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  ) numbers
  INNER JOIN movies ON CHAR_LENGTH(genres) - CHAR_LENGTH(REPLACE(genres, '/', '')) >= numbers.n - 1
  WHERE genres IS NOT NULL AND genres != ''
) movie_genres ON m.movie_id = movie_genres.movie_id
INNER JOIN genres g ON g.name = movie_genres.genre_name
ON DUPLICATE KEY UPDATE `movie_id` = VALUES(`movie_id`);

-- ----------------------------
-- Step 6: Migrate existing regions data
-- ----------------------------

-- Insert unique regions from movies table
INSERT INTO `regions` (`name`)
SELECT DISTINCT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(regions, '/', numbers.n), '/', -1)) AS region_name
FROM (
  SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) numbers
INNER JOIN movies ON CHAR_LENGTH(regions) - CHAR_LENGTH(REPLACE(regions, '/', '')) >= numbers.n - 1
WHERE regions IS NOT NULL AND regions != ''
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(regions, '/', numbers.n), '/', -1)) != ''
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Populate movie_region_relation table
INSERT INTO `movie_region_relation` (`movie_id`, `region_id`)
SELECT DISTINCT m.movie_id, r.id
FROM movies m
INNER JOIN (
  SELECT movie_id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(regions, '/', numbers.n), '/', -1)) AS region_name
  FROM (
    SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  ) numbers
  INNER JOIN movies ON CHAR_LENGTH(regions) - CHAR_LENGTH(REPLACE(regions, '/', '')) >= numbers.n - 1
  WHERE regions IS NOT NULL AND regions != ''
) movie_regions ON m.movie_id = movie_regions.movie_id
INNER JOIN regions r ON r.name = movie_regions.region_name
ON DUPLICATE KEY UPDATE `movie_id` = VALUES(`movie_id`);

-- ----------------------------
-- Step 7: Verification queries (optional, for testing)
-- ----------------------------

-- Check genres migration
-- SELECT COUNT(*) AS total_genres FROM genres;
-- SELECT COUNT(*) AS total_movie_genre_relations FROM movie_genre_relation;

-- Check regions migration
-- SELECT COUNT(*) AS total_regions FROM regions;
-- SELECT COUNT(*) AS total_movie_region_relations FROM movie_region_relation;

-- Sample: Get movies with their genres using new structure
-- SELECT m.movie_id, m.name, GROUP_CONCAT(g.name SEPARATOR ', ') AS genres
-- FROM movies m
-- LEFT JOIN movie_genre_relation mgr ON m.movie_id = mgr.movie_id
-- LEFT JOIN genres g ON mgr.genre_id = g.id
-- GROUP BY m.movie_id, m.name
-- LIMIT 10;

-- Sample: Get movies with their regions using new structure
-- SELECT m.movie_id, m.name, GROUP_CONCAT(r.name SEPARATOR ', ') AS regions
-- FROM movies m
-- LEFT JOIN movie_region_relation mrr ON m.movie_id = mrr.movie_id
-- LEFT JOIN regions r ON mrr.region_id = r.id
-- GROUP BY m.movie_id, m.name
-- LIMIT 10;

-- ----------------------------
-- Step 8: Optional - Drop old columns (UNCOMMENT AFTER VERIFICATION)
-- ----------------------------
-- WARNING: Only execute after thorough testing and verification
-- This will permanently remove the old genres and regions columns

-- ALTER TABLE `movies` DROP COLUMN `genres`;
-- ALTER TABLE `movies` DROP COLUMN `regions`;

-- ----------------------------
-- Notes:
-- 1. The old 'genres' and 'regions' columns in movies table are kept for backward compatibility
-- 2. After full migration and testing, you can drop them using Step 8
-- 3. Make sure to update application code to use the new normalized structure
-- 4. Consider adding foreign key constraints after migration if needed
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
