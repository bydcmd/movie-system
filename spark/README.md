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
uv run python jobs/build_ads_user_funnel_1d.py --config conf/etl_config.json --calc-date 2026-02-25
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
- `run_dwd_user_snapshot.sh` -> `jobs/build_dwd_user_snapshot_di.py`
- `run_dwd_movie_snapshot.sh` -> `jobs/build_dwd_movie_snapshot_di.py`
- `run_dws_build.sh` -> `jobs/build_dws_user_movie_metrics_di.py`
- `run_dws_postgres_interactions.sh` -> `jobs/build_dws_postgres_interactions_1d.py`
- `run_dws_profiles.sh` -> `jobs/build_dws_user_movie_profiles_1d.py`
- `run_ads_hot_movies.sh` -> `jobs/build_ads_hot_movies.py`
- `run_ads_hot_movies_pg_sync.sh` -> `jobs/sync_ads_hot_movies_to_postgres.py`
- `run_ads_als_similar_movies.sh` -> `jobs/build_ads_als_similar_movies.py`
- `run_ads_itemcf.sh` -> `jobs/build_ads_itemcf_recommendations.py`
- `run_ads_itemcf_similar_movies_pg_sync.sh` -> `jobs/sync_ads_itemcf_similar_movies_to_postgres.py`
- `run_ads_genre_preference.sh` -> `jobs/build_ads_genre_preference_1d.py`
- `run_ads_search_funnel.sh` -> `jobs/build_ads_search_funnel_1d.py`
- `run_ads_search_keyword_insights.sh` -> `jobs/build_ads_search_keyword_insights_1d.py`
- `run_ads_user_funnel.sh` -> `jobs/build_ads_user_funnel_1d.py`
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

Here `--movie-count` means how many existing rows to sample from `public.movies`, not how many new movies to create.
`--user-count` means how many existing rows to sample from `public.users`; additional registered users are still constructed from the generator itself.

Fixture-only export:

```bash
bash run_generate_dwd_user_event_source_data.sh \
  --batch-date 2026-02-25 \
  --write-mode fixtures \
  --fixture-dir fixtures/dwd_seed
```

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

Two PostgreSQL-driven DWD snapshot jobs are also available:

### 5.1 User snapshot (`dwd.dwd_user_snapshot_di`)

This snapshot intentionally excludes the raw password field and keeps only analytics-safe user profile attributes.

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_dwd_user_snapshot_di.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25 \
  --snapshot-date 2026-02-25
```

If `--snapshot-date` is omitted, the job resolves the latest available `ods.ods_pg_users_full.dt` partition with `dt <= calc-date`.

### 5.2 Movie snapshot (`dwd.dwd_movie_snapshot_di`)

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_dwd_movie_snapshot_di.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25 \
  --snapshot-date 2026-02-25
```

If `--snapshot-date` is omitted, the job resolves the latest available `ods.ods_pg_movies_full.dt` partition with `dt <= calc-date`.

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

Sync ADS hot rankings to PostgreSQL table `public.stats_hot_movies`:

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --packages org.postgresql:postgresql:42.7.3 \
  jobs/sync_ads_hot_movies_to_postgres.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

The sync job writes `DAILY`, `WEEKLY`, `MONTHLY`, and `TOTAL` rows from `ads.ads_hot_movies.dt=calc-date`.
It deletes existing rows for the same `calc_date` before appending, so reruns stay idempotent under the unique key `(movie_id, period_type, calc_date)`.

Wrapper script:

```bash
bash run_ads_hot_movies_pg_sync.sh 2026-02-25 conf/etl_config.json
```

Important:

- `ads.source_type=movie_metric_daily` is the recommended mode when weekly and monthly rankings must reflect the true recent 7/30 day windows; in this mode `TOTAL` aggregates all available daily partitions up to `calc-date`.
- `ads.source_type=movie_metric_snapshot` still labels one full snapshot as `DAILY / WEEKLY / MONTHLY / TOTAL` for compatibility, but those periods share the same full-data snapshot metrics.
- Old Hive partitions generated before this change may still only contain `period_type='SNAPSHOT'`; rebuild those dates before syncing them to PostgreSQL.

## 8) Build more ADS reports

Three additional ADS jobs are available:

### 8.1 Daily user funnel (`ads.ads_user_funnel_1d`)

This report now also includes `favorite_folder_action_users` (daily users who performed favorite-folder create/update/share/delete actions).

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

### 9.1 Build ALS similar movies (Spark + Hive)

This job trains implicit ALS from `dws.dws_user_item_preference_1d`, extracts `itemFactors`, then computes cosine TopK neighbors from normalized item latent vectors.

Output:

- ALS similar movies: `ads.ads_itemcf_similar_movies` with `similarity_type=3`

The job preserves existing rows for other `similarity_type` values in the same `dt` partition and only replaces the ALS snapshot for `similarity_type=3`.

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_als_similar_movies.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

Optional override top K similar movies:

```bash
--top-k 80
```

Main parameters are configurable in `ads_als_similar_movies`:

- `positive_score_weights`: positive-only implicit feedback weights used to build ALS confidence
- `score_transform`: transform applied to the raw positive score, default `log1p`
- `min_user_positive_items`: minimum number of positive movies per user kept for training
- `min_item_positive_users`: minimum number of positive users per movie kept for training
- `als_params`: ALS hyperparameters such as `rank`, `max_iter`, `reg_param`, and `alpha`
- `pairing.block_count`: number of blocks used for exact factor-pair generation

Wrapper script:

```bash
bash run_ads_als_similar_movies.sh 2026-02-25 conf/etl_config.json --top-k 80
```

Sync ADS ItemCF similar movies to PostgreSQL table `public.stats_similar_movies`:

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --packages org.postgresql:postgresql:42.7.3 \
  jobs/sync_ads_itemcf_similar_movies_to_postgres.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

The sync job writes rows from `ads.ads_itemcf_similar_movies.dt=calc-date`.
It deletes existing rows for the configured `similarity_type` values first (default: `2` for ItemCF and `3` for ALS similar movies) and then appends the new snapshot, so reruns stay idempotent under the unique key `(movie_id, similar_movie_id, similarity_type)`.

Wrapper script:

```bash
bash run_ads_itemcf_similar_movies_pg_sync.sh 2026-02-25 conf/etl_config.json
```

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

### 10.2 Search conversion funnel (`ads.ads_search_funnel_1d`)

`after_search_*` metrics are computed from actions whose `event_ts` is later than the user's first search event on the calculation day. Favorite conversion only counts favorite `ADD` actions.

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
- data: `userId`, `movieId`, `commentId`, `folderId`, `folderName`, `isPublic`, `operation`, `rating`, `searchKeyword`, `resultCount`, `filterConditions`, `searchTime`
- raw payload: `event_data`, `raw_json`

DWD (`dwd.dwd_user_event_wide_di`) further normalizes behavior flags including `is_favorite_folder_action`.

## Suggested scheduling

- Daily batch snapshot: run once per day (for example 01:30) with `--batch-date`.
- Streaming ingestion: long-running service (24x7), checkpoint persisted to HDFS.
- DWD build: run after snapshot + ODS Kafka data is ready (for example every hour or daily by `--calc-date`).
- DWS build: run after DWD partition is generated (for example hourly or daily T+0 refresh).
- ADS hot rankings: run after DWS daily movie metrics are generated (daily or rolling refresh by `--calc-date`).
