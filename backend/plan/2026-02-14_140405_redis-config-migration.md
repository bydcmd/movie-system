# Redis 配置迁移到 spring.data.redis

生成时间：2026-02-14 14:04:05
文件名：2026-02-14_140405_redis-config-migration.md

## 任务背景
Spring Boot 3.5.10 中 spring.redis.* 已弃用，需要迁移到 spring.data.redis.*，消除告警并保持现有 Redis 连接与缓存行为。

## 方案详情

### 现状分析
- pplication-dev.yml 中 Redis 配置位于 spring.redis。
- 缓存配置位于 spring.cache.redis，无需变更。

### 技术方案
- 将 spring.redis 整段迁移为 spring.data.redis，仅调整层级与前缀，不改值。
- 保留 lettuce.pool 及超时等参数。

### 影响范围
- src/main/resources/application-dev.yml

## 原子步骤清单

### 步骤 1：迁移 Redis 配置前缀
- **操作对象**：src/main/resources/application-dev.yml
- **具体动作**：将 spring.redis 迁移为 spring.data.redis，保持所有参数值与注释
- **预期结果**：配置适配 Spring Boot 3.5.10
- **关键里程碑**：是

### 步骤 2：复查缓存配置与层级
- **操作对象**：src/main/resources/application-dev.yml
- **具体动作**：确认 spring.cache.redis 未被改动，缩进层级正确
- **预期结果**：缓存行为保持不变
- **关键里程碑**：否

## 预期结果
- 相关弃用告警消除
- Redis 连接与缓存行为与原先一致