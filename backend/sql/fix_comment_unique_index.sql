-- 修复评论功能的并发问题
-- 添加唯一索引防止用户重复发表评论

-- 1. 清理可能存在的重复数据（保留最新的一条）
-- 对于短评 (type = 1)
DELETE c1 FROM comments c1
INNER JOIN comments c2 
WHERE c1.comment_id < c2.comment_id 
  AND c1.user_id = c2.user_id 
  AND c1.movie_id = c2.movie_id 
  AND c1.type = c2.type 
  AND c1.type = 1;

-- 对于长评 (type = 2)  
DELETE c1 FROM comments c1
INNER JOIN comments c2 
WHERE c1.comment_id < c2.comment_id 
  AND c1.user_id = c2.user_id 
  AND c1.movie_id = c2.movie_id 
  AND c1.type = c2.type 
  AND c1.type = 2;

-- 2. 添加唯一索引 uk_user_movie_type
-- 这个索引保证一个用户对同一部电影只能发一条短评和一条长评
ALTER TABLE comments 
ADD UNIQUE INDEX uk_user_movie_type (user_id ASC, movie_id ASC, type ASC) USING BTREE;

-- 3. 为评论表添加版本号字段，用于乐观锁控制并发更新 votes
-- 先检查字段是否已存在
SET @dbname = DATABASE();
SET @tablename = 'comments';
SET @columnname = 'version';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) = 0,
  'ALTER TABLE comments ADD COLUMN version INT NULL DEFAULT 0 COMMENT "乐观锁版本号";',
  'SELECT 1;'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
