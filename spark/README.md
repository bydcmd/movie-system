# Movie Spark ETL (PostgreSQL + Kafka -> Hive)

This directory contains Spark ETL jobs that load:

- PostgreSQL snapshot tables into Hive ODS partitions (`dt`).
- Kafka event logs from backend event tracking into Hive ODS partitions (`dt`, `hh`).

## 1) Prepare config

Copy and edit config:

```bash
cp conf/etl_config.example.json conf/etl_config.json
```

Set PostgreSQL JDBC info, Kafka brokers, and HDFS locations.

## 2) Create Hive ODS tables

Run:

```bash
hive -f ../hive/ods/ods_pg_kafka_ddl.hql
```

## 3) Run PostgreSQL batch ingestion

`postgres_to_hive_ods.py` writes to `.../dt=YYYY-MM-DD` and then adds Hive partition.

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --packages org.postgresql:postgresql:42.7.3 \
  jobs/postgres_to_hive_ods.py \
  --config conf/etl_config.json \
  --batch-date 2026-02-25
```

Optional table filter:

```bash
--tables public.movies,public.users,public.ratings
```

## 4) Run Kafka streaming ingestion

`kafka_events_to_hive_ods.py` consumes all configured topics and writes ORC files partitioned by `dt` and `hh`.

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.2 \
  jobs/kafka_events_to_hive_ods.py \
  --config conf/etl_config.json
```

For scheduler-driven bounded runs (consume current backlog then exit):

```bash
--run-mode available-now
```

## 5) Build DWD wide table

Create DWD table first:

```bash
hive -f ../hive/dwd/dwd_ddl.hql
```

Build daily event wide table (`dwd.dwd_user_event_wide_di`) by joining Kafka behavior with PostgreSQL snapshot dimensions:

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_dwd_user_event_wide_di.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25 \
  --snapshot-date 2026-02-25
```

## 6) Build DWS aggregates

Create DWS tables:

```bash
hive -f ../hive/dws/dws_ddl.hql
```

Build daily user/movie/event-type aggregates from DWD:

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_dws_user_movie_metrics_di.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

## 7) Build ADS hot rankings

Create ADS table:

```bash
hive -f ../hive/ads/ads_ddl.hql
```

Build daily/weekly/monthly hot ranking from `dws.dws_movie_action_1d`:

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_hot_movies.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

Optional override top N result rows per period:

```bash
--top-n 200
```

## 8) Build more ADS reports

Three additional ADS jobs are available:

### 8.1 Daily user funnel (`ads.ads_user_funnel_1d`)

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_user_funnel_1d.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

### 8.2 User retention (`ads.ads_user_retention`)

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_user_retention.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

Retention windows are configurable in `ads_user_retention.retention_days` (default: `[1, 7, 30]`).

### 8.3 Daily genre preference ranking (`ads.ads_genre_preference_1d`)

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_genre_preference_1d.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

Optional override top N genres:

```bash
--top-n 50
```

## 9) Build ItemCF recommendations (Spark + Hive)

Generate both outputs in one run:

- item-item similarity: `ads.ads_itemcf_similar_movies`
- personalized recommendations: `ads.ads_itemcf_user_recommendations`

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_itemcf_recommendations.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

Optional overrides:

```bash
--top-k 120 --top-n 50
```

Main parameters are configurable in `ads_itemcf`:

- `lookback_days`: training window size
- `min_user_item_score`: minimum user-item preference score
- `min_co_users`: minimum common users for item pairs
- `shrinkage`: similarity shrinkage factor
- `event_score_weights`: behavior-to-preference scoring weights

## 10) Build search & recommendation mining reports

### 10.1 User-item recommendation features (`ads.ads_reco_user_item_features_1d`)

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_reco_user_item_features_1d.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

### 10.2 Search keyword insights (`ads.ads_search_keyword_insights_1d`)

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_search_keyword_insights_1d.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

Optional top N keywords:

```bash
--top-n 200
```

### 10.3 Search conversion funnel (`ads.ads_search_funnel_1d`)

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_search_funnel_1d.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

## Kafka event mapping

Source event contract is based on:

- `backend/src/main/java/com/movie/backend/messaging/event/EventEnvelope.java`
- `backend/src/main/java/com/movie/backend/messaging/event/EventType.java`
- `backend/src/main/java/com/movie/backend/messaging/event/*.java`

The streaming job extracts common fields:

- envelope: `eventId`, `eventType`, `occurredAt`
- data: `userId`, `movieId`, `commentId`, `folderId`, `operation`, `rating`, `searchKeyword`, `resultCount`
- raw payload: `event_data`, `raw_json`

## Suggested scheduling

- Daily batch snapshot: run once per day (for example 01:30) with `--batch-date`.
- Streaming ingestion: long-running service (24x7), checkpoint persisted to HDFS.
- DWD build: run after snapshot + ODS Kafka data is ready (for example every hour or daily by `--calc-date`).
- DWS build: run after DWD partition is generated (for example hourly or daily T+0 refresh).
- ADS hot rankings: run after DWS daily movie metrics are generated (daily or rolling refresh by `--calc-date`).
