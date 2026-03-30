-- ============================================
-- 默认收藏夹数据迁移脚本
-- 将虚拟默认收藏夹（folderId=0）迁移为真实收藏夹记录
-- ============================================

-- 1. 添加 is_default 字段到 favorite_folders 表（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'favorite_folders' AND column_name = 'is_default'
    ) THEN
        ALTER TABLE favorite_folders ADD COLUMN is_default INTEGER DEFAULT 0;
        COMMENT ON COLUMN favorite_folders.is_default IS '是否为默认收藏夹：0-否, 1-是';
    END IF;
END $$;

-- 2. 为每个有 folderId=0 收藏记录的用户创建默认收藏夹
-- 使用 INSERT ... SELECT 确保每个用户只创建一个默认收藏夹
INSERT INTO favorite_folders (user_id, name, description, is_public, is_default, movie_count, create_time, update_time)
SELECT 
    f.user_id,
    '默认收藏夹',
    '系统自动创建的默认收藏夹',
    0,
    1,
    (SELECT COUNT(*) FROM favorites f2 WHERE f2.user_id = f.user_id AND f2.folder_id = 0),
    COALESCE((SELECT MIN(create_time) FROM favorites f3 WHERE f3.user_id = f.user_id), NOW()),
    NOW()
FROM favorites f
WHERE f.folder_id = 0
AND NOT EXISTS (
    SELECT 1 FROM favorite_folders ff 
    WHERE ff.user_id = f.user_id AND ff.is_default = 1
)
GROUP BY f.user_id;

-- 3. 更新 favorites 表中的 folderId
-- 将 folder_id=0 的记录更新为真实的默认收藏夹 ID
UPDATE favorites f
SET folder_id = (
    SELECT ff.id FROM favorite_folders ff 
    WHERE ff.user_id = f.user_id AND ff.is_default = 1
    LIMIT 1
)
WHERE f.folder_id = 0
AND EXISTS (
    SELECT 1 FROM favorite_folders ff 
    WHERE ff.user_id = f.user_id AND ff.is_default = 1
);

-- 4. 验证迁移结果
-- 检查是否还有 folder_id=0 的记录
SELECT COUNT(*) AS remaining_zero_folder_id FROM favorites WHERE folder_id = 0;

-- 检查默认收藏夹创建情况
SELECT user_id, id, name, movie_count, is_default 
FROM favorite_folders 
WHERE is_default = 1
ORDER BY create_time DESC
LIMIT 10;

-- ============================================
-- 回滚脚本（如需回滚）
-- ============================================
/*
-- 1. 将默认收藏夹的记录改回 folder_id=0
UPDATE favorites f
SET folder_id = 0
WHERE folder_id IN (
    SELECT id FROM favorite_folders WHERE is_default = 1
);

-- 2. 删除默认收藏夹记录
DELETE FROM favorite_folders WHERE is_default = 1;

-- 3. 删除 is_default 字段（可选）
-- ALTER TABLE favorite_folders DROP COLUMN is_default;
*/
