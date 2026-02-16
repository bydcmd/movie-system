# 项目概览（movie-system）

## 项目目的
- 该仓库包含电影系统的数据处理与推荐相关任务，核心是 Spark 任务的 Python 化改造与调度方案（PySpark + DolphinScheduler）。
- Spark 侧提供三类任务：热度排行计算、评分融合同步、实时推荐（Kafka → Redis）。

## 技术栈（已在仓库文档中出现）
- 数据处理：PySpark、Spark 3.x、Hive、MySQL
- 调度/编排：DolphinScheduler
- 实时/缓存：Kafka（实时推荐任务）、Redis
- 后端：Spring Boot（pom.xml 显示为 Spring Boot 3.x + MyBatis + Redis + Kafka + OpenAPI）
- 前端：Vue 3 + Tailwind CSS + naive ui + Pinia + TanStack Query + ECharts + Vite + pnpm + Tiptap + TypeScript + openapi-typescript

## 目录结构概览
- `spark/`：Spark 任务与工具（`jobs/`、`utils/`、`conf/`）
- `dolphinscheduler/`：工作流定义
- `hive/`：Hive 相关内容
- `backend/`：Java 后端（Maven 项目）
- `frontend/`：前端技术栈说明与文档

来源：`spark/README.md`、`frontend/technology_stack.md`、`backend/pom.xml`
