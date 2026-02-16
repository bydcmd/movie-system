# 评论功能问题修复总结

## 修复的问题

### 1. 竞态条件（Race Condition）⭐ 严重

**问题**：`submitComment` 和 `submitLongReview` 方法使用"检查-然后-执行"模式：
```java
// 先检查
if (commentMapper.countByUserAndMovieAndType(userId, movieId, 1) > 0) {
    throw new RuntimeException("您已经发表过短评了，无法重复发布");
}
// 再插入
commentMapper.insert(comment);
```

**后果**：高并发时，两个请求可能同时通过检查，导致一个用户可以发表多条评论。

**修复方案**：
- 删除前置检查查询
- 依赖数据库唯一索引保证幂等性
- 捕获 `DuplicateKeyException` 异常返回友好提示

```java
@Transactional(rollbackFor = Exception.class)
public void submitComment(String userId, Long movieId, String content) {
    // ... 参数校验 ...
    try {
        Comment comment = new Comment();
        // ... 设置属性 ...
        comment.setVersion(0); // 初始版本号
        commentMapper.insert(comment);
    } catch (DuplicateKeyException e) {
        throw new RuntimeException("您已经发表过短评了，无法重复发布");
    }
}
```

### 2. 点赞功能并发问题 ⭐ 中等

**问题**：`toggleLike` 方法的异常处理会抛出运行时异常，但此时 votes 可能已被修改。

**修复方案**：
- 改进异常处理逻辑，在捕获 `DuplicateKeyException` 时返回当前实际状态
- 确保事务回滚时状态一致性

```java
@Transactional(rollbackFor = Exception.class)
public boolean toggleLike(String userId, Long commentId) {
    CommentLike existing = commentLikeMapper.selectByCommentAndUser(commentId, userId);
    
    if (existing != null) {
        // 取消点赞
        commentLikeMapper.delete(commentId, userId);
        commentMapper.updateVotes(commentId, -1);
        return false;
    } else {
        try {
            // 点赞
            commentLikeMapper.insert(like);
            commentMapper.updateVotes(commentId, 1);
            return true;
        } catch (DuplicateKeyException e) {
            // 并发冲突，返回当前实际状态
            return true;
        }
    }
}
```

### 3. 缺少参数校验 ⭐ 中等

**问题**：
- `updateCommentWithRating` 未校验 `rating` 参数（可能为 null 或超出范围）
- `submitLongReview` 未校验 `content` 长度

**修复方案**：
```java
public void updateCommentWithRating(String userId, Long movieId, String content, Integer rating) {
    if (rating == null) {
        throw new IllegalArgumentException("评分不能为空");
    }
    if (rating < 1 || rating > 5) {
        throw new IllegalArgumentException("评分必须在 1 到 5 之间");
    }
    if (!StringUtils.hasText(content)) {
        throw new IllegalArgumentException("评论内容不能为空");
    }
    if (content.length() > 500) {
        throw new IllegalArgumentException("短评内容不能超过500字");
    }
    // ...
}
```

### 4. 事务一致性 ⭐ 中等

**问题**：部分 `@Transactional` 注解缺少 `rollbackFor = Exception.class`，导致某些异常不会触发回滚。

**修复方案**：
将所有 `@Transactional` 统一为：
```java
@Transactional(rollbackFor = Exception.class)
```

### 5. 乐观锁支持（预留）⭐ 低

**问题**：`updateVotes` 在高并发下可能存在计数不准确。

**修复方案**：
- 为 `Comment` 实体添加 `version` 字段
- 添加 `updateVotesWithVersion` 方法支持乐观锁

```java
// Comment.java
private Integer version;

// CommentMapper.java
int updateVotesWithVersion(@Param("id") Long id, @Param("delta") int delta, @Param("version") Integer version);

// CommentMapper.xml
<update id="updateVotesWithVersion">
    UPDATE comments 
    SET votes = votes + #{delta}, version = version + 1
    WHERE comment_id = #{id} AND version = #{version}
</update>
```

## 数据库变更

执行 `sql/fix_comment_unique_index.sql`：

1. **清理重复数据**（保留最新的一条）
2. **添加唯一索引**：`uk_user_movie_type(user_id, movie_id, type)`
3. **添加版本号字段**：`version INT DEFAULT 0`

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `CommentServiceImpl.java` | 修改 | 修复竞态条件、并发问题、参数校验 |
| `Comment.java` | 修改 | 添加 version 字段 |
| `CommentMapper.java` | 修改 | 添加 updateVotesWithVersion 方法 |
| `CommentMapper.xml` | 修改 | 添加 version 映射和乐观锁更新 |
| `fix_comment_unique_index.sql` | 新增 | 数据库迁移脚本 |

## 部署步骤

1. **执行数据库迁移**（注意：先备份数据）
   ```bash
   mysql -u root -p movie_db < sql/fix_comment_unique_index.sql
   ```

2. **部署代码更新**
   - 编译打包
   - 重启服务

3. **验证**
   - 测试发表评论（重复发表应被拒绝）
   - 测试点赞功能
   - 测试修改评论和评分
