# 建议常用命令

## Spark 任务（本地调试）
- `spark-submit --master local[*] jobs/hot_movies_calc.py --calc-date 2026-02-12`
- `spark-submit --master local[*] jobs/rating_sync.py`
- `spark-submit --master local[*] --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.2 jobs/realtime_recs.py --kafka-bootstrap ...`

## 依赖安装（Spark 侧）
- `pip install pyspark==3.4.2`
- `pip install pymysql`
- `pip install redis`

## 生产/调度相关
- `./submit_job.sh hot_movies_calc --date 2026-02-12`
- DolphinScheduler：导入 `dolphinscheduler/workflows/movie_analytics_workflow.json`

## 后端（Maven 项目）
- 仓库包含 `backend/pom.xml`（Maven 项目）。具体构建/测试命令未在仓库文档中明确给出，常见命令如 `mvn test`、`mvn spring-boot:run` 需结合团队约定确认。

来源：`spark/README.md`、`backend/pom.xml`
