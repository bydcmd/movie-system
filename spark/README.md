# Spark 任务重构方案 (Python + DolphinScheduler)

## 一、方案概述

### 1.1 为什么用 Python 替代 Java？

| 对比项 | Java/Scala | Python (PySpark) |
|--------|-----------|------------------|
| **代码量** | 119-146行 | ~200行（含详细注释） |
| **开发效率** | 低，需编译打包 | ✅ 高，脚本直接运行 |
| **参数调整** | 改代码→打包→部署 | ✅ 命令行参数直接改 |
| **SQL编写** | 繁琐，需API链式调用 | ✅ 原生SQL字符串 |
| **调试** | 需打包后测试 | ✅ 本地直接运行 |
| **维护成本** | 高（Maven依赖管理） | ✅ 低（pip管理） |

### 1.2 技术栈

```
PySpark + Hive + MySQL + DolphinScheduler
```

---

## 二、目录结构

```
spark/
├── README.md                      # 本说明文档
├── submit_job.sh                  # Spark任务提交脚本
├── conf/
│   └── spark-env.conf             # 环境配置文件
├── jobs/                          # 任务脚本目录
│   ├── hot_movies_calc.py         # 热度计算任务
│   ├── rating_sync.py             # 评分融合同步任务
│   └── realtime_recs.py           # 实时推荐任务 (Kafka + Redis)
└── utils/                         # 工具模块
    └── mysql_utils.py             # MySQL操作工具

dolphinscheduler/
└── workflows/
    └── movie_analytics_workflow.json  # DolphinScheduler工作流定义
```

---

## 三、任务说明

### 3.1 热度计算任务 (hot_movies_calc.py)

**功能**: 计算电影的日/周/月热度排行榜

**算法公式**:
```
HotScore = (ViewCount * 0.1) + (CommentCount * 0.5) + (RatingCount * 0.3) + (AvgRating * 1.0)
```

**参数**:
```bash
--jdbc-url      MySQL连接URL
--db-user       数据库用户名
--db-password   数据库密码
--hive-db       Hive数据库名 (默认: ods_movie_db)
--w-view        浏览量权重 (默认: 0.1)
--w-comment     评论权重 (默认: 0.5)
--w-rating      评分数权重 (默认: 0.3)
--w-score       平均分权重 (默认: 1.0)
--calc-date     计算日期 (默认: 今天)
```

**示例**:
```bash
# 本地调试
spark-submit --master local[*] jobs/hot_movies_calc.py --calc-date 2026-02-12

# 生产环境
./submit_job.sh hot_movies_calc --date 2026-02-12
```

### 3.2 评分融合同步任务 (rating_sync.py)

**功能**: 融合豆瓣评分和本站评分，更新 movie.score

**算法公式** (贝叶斯加权平均):
```
如果 本站评分人数 < 10: 使用豆瓣分
否则: (豆瓣分 * K + 本站平均分 * 本站人数) / (K + 本站人数)
      (K=50, 相当于增加50个"隐形豆瓣用户")
```

**参数**:
```bash
--jdbc-url      MySQL连接URL
--db-user       数据库用户名
--db-password   数据库密码
--hive-db       Hive数据库名
--min-votes     起算阈值 (默认: 10)
--smooth-k      平滑常数K (默认: 50)
```

**示例**:
```bash
# 本地调试
spark-submit --master local[*] jobs/rating_sync.py

# 调整参数
spark-submit jobs/rating_sync.py --min-votes 20 --smooth-k 100
```

### 3.3 实时推荐任务 (realtime_recs.py)

**功能**: 消费 Kafka 行为事件，实时更新用户推荐结果到 Redis

**核心流程**:
1. 读取 Kafka 事件 (浏览/评分/收藏/评论)
2. 计算事件权重
3. 关联 `stats_similar_movies` 生成候选集
4. 增量写入 Redis `ZSET` (key: `recs:realtime:{userId}`)

**参数**:
```bash
--kafka-bootstrap      Kafka地址
--kafka-topics         Kafka topics (逗号分隔)
--jdbc-url             MySQL JDBC URL (读取 stats_similar_movies)
--db-user              MySQL用户名
--db-password          MySQL密码
--redis-host           Redis地址
--redis-port           Redis端口
--redis-db             Redis库
--redis-password       Redis密码(可选)
--redis-key-prefix     Redis key前缀 (默认: recs:realtime:)
--redis-ttl-hours      结果缓存时长 (小时)
--similarity-types     相似类型 (1,2)
--max-similar-per-movie  每部电影保留的相似数量
--top-n                每个用户保留的推荐数量
--w-view               浏览权重
--w-rating             评分权重(按 rating/5 缩放)
--w-favorite           收藏权重
--w-comment            评论权重
--checkpoint           Spark checkpoint 目录
```

**示例**:
```bash
spark-submit \
  --master local[*] \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.2 \
  jobs/realtime_recs.py \
  --kafka-bootstrap 127.0.0.1:9092 \
  --kafka-topics movie-view-history,movie-rating-events,movie-comment-events,movie-favorite-events \
  --jdbc-url jdbc:mysql://localhost:3306/movie_db?useSSL=false \
  --db-user root \
  --db-password xxx \
  --redis-host 127.0.0.1 \
  --redis-port 6379 \
  --checkpoint /tmp/spark/checkpoints/realtime_recs
```

---

## 四、部署方式

### 4.1 方式一: DolphinScheduler 调度 (推荐)

**优势**:
- 可视化工作流编排
- 失败重试、告警通知
- 依赖管理（任务间依赖）
- 全局参数管理

**配置步骤**:

1. **上传资源文件**
   ```
   资源中心 > 文件管理
   ├── spark/jobs/hot_movies_calc.py
   └── spark/jobs/rating_sync.py
   ```

2. **导入工作流**
   ```
   项目管理 > 工作流定义 > 导入
   选择: dolphinscheduler/workflows/movie_analytics_workflow.json
   ```

3. **配置数据源**
   - MySQL数据源 (movie_db)
   - Hive数据源 (ods_movie_db)

4. **设置全局参数**
   ```properties
   jdbc_url=jdbc:mysql://localhost:3306/movie_db
   db_user=root
   db_password=${movie_db_password}  # 使用Dolphin的密码管理
   hive_db=ods_movie_db
   ```

5. **设置定时调度**
   ```
   每天凌晨 2:00 执行
   Crontab: 0 2 * * * ?
   ```

**工作流节点**:
```
[数据质量检查] --┬--> [热度计算] --\
                |                [结果验证] --> [成功通知]
                └--> [评分融合] --/
```

### 4.2 方式二: Crontab + Shell 脚本

**适合场景**: 轻量级部署，无DolphinScheduler环境

**配置**:
```bash
# 编辑crontab
crontab -e

# 添加定时任务 (每天凌晨2点执行)
0 2 * * * cd /path/to/spark && ./submit_job.sh hot_movies_calc >> logs/hot_movies_$(date +\%Y\%m\%d).log 2>&1
30 2 * * * cd /path/to/spark && ./submit_job.sh rating_sync >> logs/rating_sync_$(date +\%Y\%m\%d).log 2>&1
```

---

## 五、环境依赖

### 5.1 必需组件

```bash
# 1. Python 3.7+
python --version

# 2. PySpark
pip install pyspark==3.4.2

# 3. PyMySQL (用于结果回写)
pip install pymysql

# 3.1 Redis Client (实时推荐写入Redis)
pip install redis

# 4. Java 8+
java -version

# 5. Spark 3.x
spark-submit --version
```

**Kafka依赖**:
```bash
spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.2 ...
```

### 5.2 自定义工具模块说明

**注意**: `utils/mysql_utils.py` 是**自定义工具类**，不是Spark自带的。

Spark本身只提供基础的JDBC支持：
```python
# Spark自带的JDBC读写
df.write.format("jdbc").option("url", jdbc_url).option("dbtable", "movies").save()
df = spark.read.format("jdbc").option("url", jdbc_url).option("dbtable", "movies").load()
```

但Spark**不支持原生的UPDATE操作**，所以需要自定义工具类来实现批量更新。

### 5.2 配置文件 (conf/spark-env.conf)

```bash
# MySQL配置
export JDBC_URL="jdbc:mysql://localhost:3306/movie_db?useSSL=false"
export DB_USER="root"
export DB_PASSWORD="your_password"

# Hive配置
export HIVE_DB="ods_movie_db"

# Spark配置
export SPARK_HOME="/opt/spark"
export SPARK_MASTER="yarn"  # 或 local
```

---

## 六、本地开发调试

### 6.1 启动本地Spark

```bash
# 安装PySpark (包含Spark运行环境)
pip install pyspark

# 本地模式运行 (无需集群)
spark-submit \
    --master local[*] \
    --driver-memory 4G \
    jobs/hot_movies_calc.py \
    --calc-date 2026-02-12 \
    --jdbc-url jdbc:mysql://localhost:3306/movie_db \
    --db-user root \
    --db-password xxx
```

### 6.2 IDE调试 (PyCharm/VSCode)

```python
# 在脚本中添加调试入口
if __name__ == '__main__':
    # 模拟参数
    import sys
    sys.argv = [
        'hot_movies_calc.py',
        '--calc-date', '2026-02-12',
        '--jdbc-url', 'jdbc:mysql://localhost:3306/movie_db',
        '--db-user', 'root',
        '--db-password', 'xxx'
    ]
    main()
```

---

## 七、性能优化

### 7.1 Spark参数调优

```bash
spark-submit \
    --driver-memory 4G \
    --executor-memory 4G \
    --executor-cores 2 \
    --num-executors 4 \
    --conf spark.sql.adaptive.enabled=true \
    --conf spark.sql.adaptive.coalescePartitions.enabled=true \
    --conf spark.sql.adaptive.skewJoin.enabled=true \
    jobs/hot_movies_calc.py
```

### 7.2 数据倾斜处理

```python
# 如果某些热门电影数据量特别大，添加盐值处理
from pyspark.sql.functions import rand

df_salted = df.withColumn("salt", (rand() * 10).cast("int"))
```

---

## 八、监控告警

### 8.1 日志查看

```bash
# YARN模式查看日志
yarn logs -applicationId application_xxx

# 或配置日志聚合到HDFS
hdfs dfs -cat /spark-logs/...
```

### 8.2 告警配置 (DolphinScheduler)

```json
{
  "timeoutNotifyStrategy": ["WARN", "FAILED"],
  "timeout": 1800,
  "receivers": "data-team@movie.com"
}
```

---

## 九、与原Java版本对比

| 特性 | Java版本 | Python版本 |
|------|---------|-----------|
| 代码行数 | 119-146行 | ~200行（含注释） |
| 打包部署 | Maven打包JAR | 直接上传.py文件 |
| 参数修改 | 改代码重编译 | 命令行参数 |
| 调试难度 | 高 | 低 |
| SQL可读性 | API链式，较乱 | 原生SQL字符串 |
| 性能 | JVM原生，略高 | 差异不大（<5%） |
| 生态集成 | 大数据组件 | 机器学习库 |
| 运维成本 | 高 | ✅ 低 |

---

## 十、迁移建议

### 10.1 如果你已经用Java运行稳定

→ **建议**: 保持现状，新任务用Python开发

### 10.2 如果你经常调整参数/算法

→ **建议**: 迁移到Python，提高迭代效率

### 10.3 如果团队以Python为主

→ **建议**: 全部迁移，统一技术栈

---

## 十一、常见问题

**Q: Python版本性能会不会差很多？**
A: 对于SQL类任务，性能差异<5%。你的两个任务主要是SQL聚合，Python完全够用。

**Q: 如何处理MySQL连接？**
A: 使用PyMySQL在Driver端批量更新，大数据量时分批处理（已内置500条/批）。

**Q: 任务失败了怎么重跑？**
A: DolphinScheduler支持失败重试配置，或手动触发补数（指定--calc-date）。

---

## 十二、后续扩展

1. **接入机器学习**: Python更容易集成sklearn/xgboost做推荐算法
2. **实时计算**: 可扩展到Spark Streaming/Flink
3. **数据血缘**: 集成Apache Atlas管理数据血缘
