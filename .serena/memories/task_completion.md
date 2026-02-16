# 完成任务时的建议动作

- Spark 作业修改后：优先用本地 `spark-submit` 验证核心路径（热度计算 / 评分同步 / 实时推荐）。
- 若涉及实时推荐：确保 Kafka / Redis 连通性，并检查 checkpoint 目录配置是否可写。
- 后端与前端未在仓库文档中提供统一的 lint/format/test 指令；如需执行，请先确认团队约定或补充脚本。

来源：`spark/README.md`
