# Favorites 表 Schema 修复说明

## 问题描述

在原有的数据库设计中，`favorites` 表存在一个严重的设计缺陷：

### 原始表结构
```sql
CREATE TABLE `favorites` (
  `user_id` varchar(100) NOT NULL,
  `movie_id` bigint NOT NULL,
  `folder_id` bigint NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `movie_id`),
  UNIQUE INDEX `uk_user_movie_folder`(`user_id`, `movie_id`, `folder_id`)
);
```

### 问题分析

1. **主键设计错误**: `PRIMARY KEY (user_id, movie_id)`
   - 这意味着一个用户对同一部电影只能有一条收藏记录
   - 无法将同一电影添加到多个收藏夹中

2. **ON DUPLICATE KEY UPDATE 副作用**:
   ```sql
   INSERT INTO favorites (...) VALUES (...)
   ON DUPLICATE KEY UPDATE create_time = VALUES(create_time)
   ```
   - 当尝试将已收藏的电影添加到另一个收藏夹时
   - 不会报错，而是更新现有记录的 `folder_id`
   - 导致电影从原收藏夹"移动"到新收藏夹

3. **前端显示不一致**:
   - 前端执行"复制"或"移动"操作后
   - 本地状态显示操作成功
   - 但刷新后发现数据库中电影位置与预期不符

## 解决方案

### 1. 数据库 Schema 修复

执行迁移脚本 `fix_favorites_schema.sql`:

```sql
-- 新的表结构
CREATE TABLE `favorites` (
  `user_id` varchar(100) NOT NULL,
  `movie_id` bigint NOT NULL,
  `folder_id` bigint NOT NULL DEFAULT 0,  -- 改为 NOT NULL，0代表默认收藏夹
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `movie_id`, `folder_id`),  -- 三元组主键
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_folder_id` (`folder_id`),
  INDEX `idx_movie_id` (`movie_id`)
);
```

**关键变更**:
- 主键改为 `(user_id, movie_id, folder_id)` - 允许同一电影在多个收藏夹中
- `folder_id` 改为 `NOT NULL DEFAULT 0` - 0代表默认收藏夹（替代原来的 NULL）
- 移除 `ON DUPLICATE KEY UPDATE` - 不再自动更新，重复插入会报错

### 2. 后端代码修改

#### FavoriteServiceImpl.java
```java
// 修改前
favorite.setFolderId(null); // 默认收藏夹

// 修改后
favorite.setFolderId(0L); // 默认收藏夹，使用0代替NULL
```

#### FavoriteMapper.xml
```xml
<!-- 移除 ON DUPLICATE KEY UPDATE -->
<insert id="insert" parameterType="com.movie.backend.entity.Favorite">
    INSERT INTO favorites (user_id, movie_id, folder_id, create_time)
    VALUES (#{userId}, #{movieId}, #{folderId}, #{createTime})
</insert>

<!-- 简化查询逻辑 -->
<select id="selectByFolderId" resultMap="MyFavoriteVOMap">
    SELECT ... FROM movies m
    JOIN favorites f ON m.movie_id = f.movie_id
    WHERE f.user_id = #{userId} 
    AND f.folder_id = #{folderId}  -- 不再需要 NULL 判断
    ORDER BY f.create_time DESC
</select>
```

### 3. 前端代码修改

#### favorites.ts
```typescript
// 新增批量删除 API
batchDeleteFavorites: async (movieIds: number[]): Promise<string> => {
    return api.delete<ApiResponse<string>, string>('/favorite/batch', {
        data: movieIds
    });
}
```

#### Favorites.tsx
```typescript
// 复制操作 - 现在可以真正复制而不是移动
if (batchActionType === 'copy') {
    // 直接添加到目标收藏夹，保留原有收藏
    for (const movieId of movieIds) {
        try {
            await favoriteApi.addFavorite(movieId, targetFolderId);
        } catch {
            // 如果已存在，忽略错误
        }
    }
}

// 移动操作 - 先删除再添加
if (batchActionType === 'move') {
    await favoriteApi.batchDeleteFavorites(movieIds);
    for (const movieId of movieIds) {
        await favoriteApi.addFavorite(movieId, targetFolderId);
    }
}
```

## 执行步骤

### 1. 备份数据库
```bash
mysqldump -u username -p movie_db > backup_before_fix_$(date +%Y%m%d).sql
```

### 2. 执行迁移脚本
```bash
mysql -u username -p movie_db < fix_favorites_schema.sql
```

### 3. 验证数据迁移
```sql
-- 检查记录数是否一致
SELECT COUNT(*) FROM favorites_backup_20260207;
SELECT COUNT(*) FROM favorites;

-- 检查 folder_id 转换
SELECT 
    COUNT(*) as null_count 
FROM favorites_backup_20260207 
WHERE folder_id IS NULL;

SELECT 
    COUNT(*) as zero_count 
FROM favorites 
WHERE folder_id = 0;
```

### 4. 部署后端代码
```bash
cd backend
mvn clean package
# 重启应用
```

### 5. 部署前端代码
```bash
cd movie-review-web
npm run build
# 部署静态文件
```

### 6. 测试验证

1. **复制功能测试**:
   - 在收藏夹 A 中选择一部电影
   - 使用"复制至"功能复制到收藏夹 B
   - 验证电影同时存在于 A 和 B 中

2. **移动功能测试**:
   - 在收藏夹 A 中选择一部电影
   - 使用"移动至"功能移动到收藏夹 B
   - 验证电影只存在于 B 中，A 中已删除

3. **批量操作测试**:
   - 选择多部电影
   - 测试批量取消收藏、复制、移动功能
   - 验证数据一致性

## 注意事项

1. **向后兼容性**:
   - 旧代码中使用 `folder_id = NULL` 的地方需要改为 `folder_id = 0`
   - 前端 API 调用不需要修改（仍然传递 `folderId` 参数）

2. **数据完整性**:
   - 迁移脚本会自动将 `NULL` 转换为 `0`
   - 三元组主键确保不会有重复记录

3. **性能优化**:
   - 添加了 `idx_user_id`, `idx_folder_id`, `idx_movie_id` 索引
   - 提高查询效率

4. **回滚方案**:
   - 保留了 `favorites_backup_20260207` 表
   - 如有问题可以快速恢复

## 功能清单

修复后支持的功能：

- ✅ 将同一电影添加到多个收藏夹
- ✅ 批量取消收藏
- ✅ 批量复制到收藏夹（保留原收藏）
- ✅ 批量移动到收藏夹（删除原收藏）
- ✅ 前端与数据库数据一致性
- ✅ 防止重复添加到同一收藏夹

## 相关文件

- 数据库迁移脚本: `backend/sql/fix_favorites_schema.sql`
- 后端服务实现: `backend/src/main/java/com/movie/backend/service/impl/FavoriteServiceImpl.java`
- MyBatis Mapper: `backend/src/main/resources/mapper/FavoriteMapper.xml`
- 前端 API: `movie-review-web/src/api/favorite.ts`
- 前端页面: `movie-review-web/src/pages/Favorites.tsx`
