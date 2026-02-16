-- 为 User 表添加 status 和 passwordVersion 字段
-- status: 账号状态 (0:正常, 1:被禁用/冻结)
-- passwordVersion: 密码版本号，用于失效旧 Token（修改密码后递增）

ALTER TABLE user 
ADD COLUMN status INT DEFAULT 0 COMMENT '账号状态 (0:正常, 1:被禁用/冻结)' AFTER role,
ADD COLUMN password_version INT DEFAULT 1 COMMENT '密码版本号，用于失效旧 Token（修改密码后递增）' AFTER status;

-- 更新现有用户数据的默认值
UPDATE user SET status = 0 WHERE status IS NULL;
UPDATE user SET password_version = 1 WHERE password_version IS NULL;

-- 添加索引以提高查询性能
CREATE INDEX idx_status ON user(status);
CREATE INDEX idx_password_version ON user(password_version);
