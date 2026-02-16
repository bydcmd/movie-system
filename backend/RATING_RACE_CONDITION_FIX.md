# 评分竞态条件解决方案 - Redis Stream 实现

## 问题描述

在原始的 `RatingServiceImpl.java` 中，更新电影平均分的逻辑存在竞态条件:

```
先读取电影信息 -> 获取所有评分 -> 计算新分数 -> 更新回数据库
```

**风险**: 如果两个用户几乎同时对同一部电影评分，两个线程都会读取到旧的 `originalVotes`，计算后覆盖更新，导致评分数据不准确。

## 解决方案

使用 **Redis Stream** 实现轻量级消息队列，由单线程消费者定期聚合更新分数。

### 架构设计

```
用户评分请求
    ↓
RatingServiceImpl (生产者)
    ↓
发送消息到 Redis Stream
    ↓
RatingScoreConsumer (单线程消费者)
    ↓
串行处理，计算并更新电影评分
```

### 核心优势

1. **消除竞态条件**: 单线程消费者串行处理所有评分更新
2. **异步解耦**: 评分提交立即返回，不阻塞用户请求
3. **可靠性**: Redis Stream 支持消息持久化和消费确认
4. **轻量级**: 无需引入 Kafka/RabbitMQ 等重量级消息中间件

## 实现文件

### 1. RatingUpdateMessage.java
评分更新消息实体，包含:
- `movieId`: 电影ID
- `operationType`: 操作类型 (CREATE/UPDATE/DELETE)
- `timestamp`: 消息时间戳
- `userId`: 用户ID（用于日志追踪）

### 2. RatingEventProducer.java
消息生产者服务:
- 发送评分更新事件到 Redis Stream
- Stream Key: `rating:update:stream`
- 异常处理: 发送失败不影响主流程

### 3. RatingScoreConsumer.java
单线程消费者:
- 从 Redis Stream 消费消息
- Consumer Group: `rating-score-group`
- 阻塞读取，超时时间 2 秒
- 每次批量处理最多 10 条消息
- 消费确认机制
- 包含完整的评分计算逻辑（从 RatingServiceImpl 迁移）

### 4. StreamConsumerRunner.java
应用启动监听器:
- 应用启动时自动启动消费者线程
- 应用关闭时优雅停止消费者

### 5. RatingServiceImpl.java (修改)
原有方法改为异步发送消息:
- `submitRating()`: 发送 CREATE 事件
- `updateRating()`: 发送 UPDATE 事件
- `clearUserRatings()`: 批量发送 DELETE 事件
- `deleteRatingsBatch()`: 批量发送 DELETE 事件
- 移除原有的 `recalculateMovieScore()` 方法（迁移到 Consumer）

## 并发测试验证

### 测试场景 1: 同时提交评分
```java
// 模拟 10 个用户同时对同一部电影评分
ExecutorService executor = Executors.newFixedThreadPool(10);
for (int i = 0; i < 10; i++) {
    final int userId = i;
    executor.submit(() -> {
        ratingService.submitRating("user" + userId, 12345L, 40);
    });
}
```

**预期结果**: 
- 所有评分都成功保存
- 电影最终评分准确（考虑所有 10 个用户的评分）
- 不会出现数据丢失或覆盖

### 测试场景 2: 混合操作
```java
// 同时进行创建、更新、删除操作
executor.submit(() -> ratingService.submitRating("user1", 12345L, 40));
executor.submit(() -> ratingService.updateRating("user2", 12345L, 35));
executor.submit(() -> ratingService.deleteRatingsBatch("user3", Arrays.asList(12345L)));
```

**预期结果**:
- 所有操作按消息队列顺序串行执行
- 最终评分准确反映实际评分状态

## 性能考量

### 优点
- **响应时间**: 用户评分请求立即返回（毫秒级）
- **吞吐量**: 单线程消费者足以应对大部分场景（QPS < 1000）
- **资源占用**: 单个消费者线程 + Redis Stream，资源消耗低

### 注意事项
1. **延迟**: 评分显示可能有 1-3 秒延迟（取决于消费者处理速度）
2. **Redis 依赖**: 需要 Redis 可用，如果 Redis 不可用，消息发送失败不影响评分保存
3. **消费者单点**: 当前实现为单实例消费者，多实例部署需要调整 Consumer Group 配置

## 监控建议

建议监控以下指标:
1. **Stream 长度**: `redis-cli XLEN rating:update:stream`
2. **消费者延迟**: 消息从产生到消费的时间差
3. **处理失败率**: 消费失败的消息数量
4. **评分更新耗时**: 单条消息处理时间

## 回滚方案

如需回滚，只需:
1. 停止 StreamConsumerRunner
2. 恢复 RatingServiceImpl 中的 `recalculateMovieScore()` 方法
3. 将所有 `sendRatingUpdateEvent()` 调用改回 `recalculateMovieScore()`

## 运行日志示例

```
[INFO] 正在启动 Redis Stream 消费者...
[INFO] 消费者组已创建: rating-score-group
[INFO] Redis Stream 消费者启动完成
[INFO] 评分更新事件已发送 - 电影ID: 12345, 用户: user1, 操作: CREATE
[INFO] 开始处理评分更新 - 电影ID: 12345, 操作: CREATE, 用户: user1
[DEBUG] 电影 12345 评分已更新: 8.5 (投票数: 1500)
[INFO] 评分更新完成 - 电影ID: 12345
```

## 总结

通过引入 Redis Stream 消息队列，成功解决了评分更新的竞态条件问题。方案具有:
- ✅ 高可靠性 (消息持久化 + 消费确认)
- ✅ 高性能 (异步处理 + 单线程串行)
- ✅ 易维护 (无需额外中间件)
- ✅ 易扩展 (可轻松增加消费者实例)
