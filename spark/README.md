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

`conf/etl_config.json` is a local working file and is intentionally ignored by Git. A local virtual environment under `spark/.venv/` is also ignored.

## Local uv runtime notes

For local execution with `uv`:

- Use Python **3.12** with `pyspark==3.4.2`.
- Run job files by path, for example:

```bash
uv run python jobs/build_ads_user_behavior_sankey_1d.py --config conf/etl_config.json --calc-date 2026-02-25
```

- If you prefer module mode, do **not** use `.py` in `-m`:

```bash
# correct
uv run python -m jobs.build_ads_itemcf_recommendations --config conf/etl_config.json --calc-date 2026-02-25

# wrong
uv run python -m build_ads_itemcf_recommendations.py
```

## Wrapper Script Naming

Spark job entrypoints live under `jobs/` and keep descriptive `build_*`, `sync_*`, or ingestion-oriented names.

Shell wrappers live at the `spark/` root as `run_*.sh` and provide a stable operator-facing entrypoint with argument parsing, defaults, and `spark-submit` flags.

Naming shorthand used in jobs:

- `_1d`: daily aggregate or profile outputs keyed by `calc-date`
- `_di`: daily partitioned detail or snapshot-style outputs using downstream Hive naming

Common wrapper to job mappings:

- `run_postgres_sync.sh` -> `jobs/postgres_to_hive_ods.py`
- `run_kafka_sync.sh` -> `jobs/kafka_events_to_hive_ods.py`
- `run_generate_dwd_user_event_source_data.sh` -> `jobs/generate_dwd_user_event_source_data.py`
- `run_dwd_build.sh` -> `jobs/build_dwd_user_event_wide_di.py`
- `run_dwd_snapshots.sh` -> `jobs/build_dwd_snapshots_di.py`
- `run_dws_build.sh` -> `jobs/build_dws_user_movie_metrics_di.py`
- `run_dws_postgres_interactions.sh` -> `jobs/build_dws_postgres_interactions_1d.py`
- `run_dws_profiles.sh` -> `jobs/build_dws_user_movie_profiles_1d.py`
- `run_ads_hot_movies.sh` -> `jobs/build_ads_hot_movies.py`
- `run_ads_pg_sync.sh` -> `jobs/sync_ads_to_postgres.py`
- `run_ads_itemcf.sh` -> `jobs/build_ads_itemcf_recommendations.py`
- `run_ads_genre_preference.sh` -> `jobs/build_ads_genre_preference_1d.py`
- `run_ads_search_keyword_insights.sh` -> `jobs/build_ads_search_keyword_insights_1d.py`
- `run_ads_user_behavior_sankey.sh` -> `jobs/build_ads_user_behavior_sankey_1d.py`
- `run_ads_user_retention.sh` -> `jobs/build_ads_user_retention.py`

## 2) Create Hive ODS tables

Run:

```bash
hive -f ../hive/ods/ods_pg_kafka_ddl.hql
```

## 2.5) Generate deterministic source data for DWD

`generate_dwd_user_event_source_data.py` creates a deterministic seed dataset for the full `dwd.dwd_user_event_wide_di` upstream chain:

- PostgreSQL business tables: `users`, `comments`, `favorite_folders`, `ratings`, `favorites`, `view_history`, `watched_movies`, `comment_likes`
- Existing PostgreSQL user and movie dimensions are sampled from `public.users` and `public.movies`
- The job only inserts constructed `user_register` users into `public.users`; sampled existing users stay read-only
- Kafka topics for all 10 event types consumed by `ods.ods_kafka_event_log_di`

The generator is idempotent for the same `batch-tag`: it first deletes the previously generated rows for that batch and then recreates them with stable IDs.

### Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--batch-date` | today | Business date (YYYY-MM-DD) |
| `--config` | required | Config file path |
| `--user-count` | 4 | Existing PostgreSQL user sample count |
| `--movie-count` | 6 | Existing PostgreSQL movie sample count |
| `--events-per-type` | 2 | Kafka event count per event type |
| `--write-mode` | both | `direct`, `fixtures`, or `both` |
| `--fixture-dir` | fixtures/dwd_user_event_source_data | Fixture output directory |
| `--batch-tag` | dwdsrc_YYYYMMDD | Optional deterministic tag |
| `--rating-bias` | 0.0 | Rating bias (-2.0 to 2.0) |
| `--validation-mode` | warn | Validation strictness (`none`, `warn`, `error`) |
| `--spark-parallelism` | (none) | Spark parallelism setting |
| `--display-registered-user-cap` | 24 | Max registered users to display |
| `--extra-login-user-cap` | 2 | Extra existing users for login events |

### Examples

Direct write plus fixture export:

```bash
bash run_generate_dwd_user_event_source_data.sh \
  --batch-date 2026-02-25 \
  --config conf/etl_config.json \
  --user-count 4 \
  --movie-count 6 \
  --events-per-type 2 \
  --write-mode both
```

Fixture-only export (no Kafka dependency):

```bash
bash run_generate_dwd_user_event_source_data.sh \
  --batch-date 2026-02-25 \
  --write-mode fixtures \
  --fixture-dir fixtures/dwd_seed
```

With rating bias and strict validation:

```bash
bash run_generate_dwd_user_event_source_data.sh \
  --rating-bias 1.5 \
  --validation-mode error
```

With custom Spark parallelism:

```bash
bash run_generate_dwd_user_event_source_data.sh \
  --spark-parallelism 8 \
  --events-per-type 10
```

Here `--movie-count` means how many existing rows to sample from `public.movies`, not how many new movies to create.
`--user-count` means how many existing rows to sample from `public.users`; additional registered users are still constructed from the generator itself.

Fixture export now also depends on the source PostgreSQL already containing the sampled `public.users` and `public.movies` rows, because those sampled dimensions are not re-exported as generated SQL.

After direct generation, the normal chain is:

1. `bash run_postgres_sync.sh 2026-02-25 conf/etl_config.json`
2. `bash run_kafka_sync.sh conf/etl_config.json available-now`
3. `bash run_dwd_build.sh 2026-02-25 conf/etl_config.json`

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

`kafka_events_to_hive_ods.py` consumes all configured topics, writes ORC files partitioned by `dt` and `hh`, and registers the touched Hive partitions in `kafka.sink_table`.

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

If `--snapshot-date` is omitted, the job resolves the latest common PostgreSQL ODS snapshot partition with `dt <= calc-date`.

PostgreSQL-driven DWD snapshot job builds both user and movie snapshots in one run:

### 5.1 User and Movie snapshots (`dwd.dwd_user_snapshot_di`, `dwd.dwd_movie_snapshot_di`)

This job builds both user and movie snapshots. The user snapshot intentionally excludes the raw password field and keeps only analytics-safe user profile attributes.

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_dwd_snapshots_di.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25 \
  --snapshot-date 2026-02-25 \
  --snapshots user,movie
```

If `--snapshot-date` is omitted, the job resolves the latest available ODS partition with `dt <= calc-date`.
The `--snapshots` argument controls which snapshots to build: `user`, `movie`, or `all` (default: `user,movie`).

Wrapper script:

```bash
bash run_dwd_snapshots.sh 2026-02-25 conf/etl_config.json
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

Build daily user/movie profile tables by combining DWD snapshots with DWS action metrics:

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_dws_user_movie_profiles_1d.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25 \
  --snapshot-date 2026-02-25
```

If `--snapshot-date` is omitted, the job resolves the latest common DWD snapshot partition with `dt <= calc-date`.

This job outputs:

- `dws.dws_user_profile_1d`
- `dws.dws_movie_profile_1d`

Wrapper script:

```bash
bash run_dws_profiles.sh 2026-02-25 conf/etl_config.json
```

Build PostgreSQL-driven interaction snapshots for recommendation and hot ranking:

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_dws_postgres_interactions_1d.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25 \
  --snapshot-date 2026-02-25
```

If `--snapshot-date` is omitted, the job resolves the latest common source snapshot partition with `dt <= calc-date`.

This job outputs:

- `dws.dws_user_item_preference_1d`
- `dws.dws_movie_engagement_1d`
- `dws.dws_movie_engagement_daily_1d`

Wrapper script:

```bash
bash run_dws_postgres_interactions.sh 2026-02-25 conf/etl_config.json
```

## 7) Build ADS hot rankings

Create ADS table:

```bash
hive -f ../hive/ads/ads_ddl.hql
```

Build hot ranking from the configured DWS source.

Recommended source:

- `dws.dws_movie_engagement_daily_1d` with `ads.source_type=movie_metric_daily`

This source is built from PostgreSQL full snapshot tables but filtered by real event timestamps on each `calc-date`, so `DAILY / WEEKLY / MONTHLY` rankings represent true recent 1/7/30 day windows, while `TOTAL` represents the full history accumulated up to `calc-date`.

Legacy Kafka-driven source is still supported:

- `dws.dws_movie_action_1d` with `ads.source_type=movie_action_daily`

Full snapshot source is still available for lifetime-style ranking snapshots:

- `dws.dws_movie_engagement_1d` with `ads.source_type=movie_metric_snapshot`

Example:

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

Sync ADS data to PostgreSQL (hot movies and similar movies):

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --packages org.postgresql:postgresql:42.7.3 \
  jobs/sync_ads_to_postgres.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25 \
  --sync-types hot_movies,similar_movies
```

The sync job supports two sync types:
- `hot_movies`: Writes `DAILY`, `WEEKLY`, `MONTHLY`, and `TOTAL` rows from `ads.ads_hot_movies.dt=calc-date` to `public.stats_hot_movies`. It deletes existing rows for the same `calc_date` before appending.
- `similar_movies`: Writes ItemCF and ALS similar movies from `ads.ads_itemcf_similar_movies.dt=calc-date` to `public.stats_similar_movies`. It deletes existing rows for the configured `similarity_type` values before appending.

The `--sync-types` argument controls which data to sync: `hot_movies`, `similar_movies`, or `all` (default: `hot_movies,similar_movies`).

Wrapper script:

```bash
bash run_ads_pg_sync.sh 2026-02-25 conf/etl_config.json
```

To sync only hot movies:

```bash
bash run_ads_pg_sync.sh 2026-02-25 conf/etl_config.json --sync-types hot_movies
```

To sync only similar movies:

```bash
bash run_ads_pg_sync.sh 2026-02-25 conf/etl_config.json --sync-types similar_movies
```

## 8) Build more ADS reports

Three additional ADS jobs are available:

### 8.1 User behavior Sankey (`ads.ads_user_behavior_sankey_1d`)

Combines search-funnel and user-funnel into a single Sankey graph. Each output row is one directed link: `(source_node, target_node, user_count)`. Actions 看过/评分/评论/收藏 are parallel (non-sequential).

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_user_behavior_sankey_1d.py \
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

Generate both outputs in one run.

Recommended source:

- `dws.dws_user_item_preference_1d` with `ads_itemcf.source_type=user_item_preference`

Legacy Kafka-driven source is still supported:

- `dwd.dwd_user_event_wide_di` with `ads_itemcf.source_type=event_wide`

Outputs:

- item-item similarity: `ads.ads_itemcf_similar_movies`

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
--top-k 120
```

Main parameters are configurable in `ads_itemcf`:

- `lookback_days`: training window size
- `min_user_item_score`: minimum user-item preference score
- `min_co_users`: minimum common users for item pairs
- `shrinkage`: similarity shrinkage factor
- `event_score_weights`: behavior-to-preference scoring weights (`favorite_add` / `favorite_remove`; legacy `favorite` is still accepted as a fallback for add weight)

## 10) Build search & recommendation mining reports

### 10.1 Search keyword insights (`ads.ads_search_keyword_insights_1d`)

Keyword-level conversion metrics attribute each action to the latest preceding search keyword for the same user on the same day, instead of copying one user's later behavior to every keyword they searched.

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

## Kafka event mapping

Source event contract is based on:

- `backend/src/main/java/com/movie/backend/messaging/event/EventEnvelope.java`
- `backend/src/main/java/com/movie/backend/messaging/event/EventType.java`
- `backend/src/main/java/com/movie/backend/messaging/event/*.java`

The streaming job extracts common fields:

- envelope: `eventId`, `eventType`, `occurredAt`
- data: `userId`, `movieId`, `commentId`, `folderId`, `folderName`, `isPublic`, `operation`, `rating`, `searchKeyword`, `resultCount`, `filterConditions`, `searchTime`
- raw payload: `event_data`, `raw_json`

DWD (`dwd.dwd_user_event_wide_di`) further normalizes behavior flags including `is_favorite_folder_action`.

## Suggested scheduling

- Daily batch snapshot: run once per day (for example 01:30) with `--batch-date`.
- Streaming ingestion: long-running service (24x7), checkpoint persisted to HDFS.
- DWD build: run after snapshot + ODS Kafka data is ready (for example every hour or daily by `--calc-date`).
- DWS build: run after DWD partition is generated (for example hourly or daily T+0 refresh).
- ADS hot rankings: run after DWS daily movie metrics are generated (daily or rolling refresh by `--calc-date`).
