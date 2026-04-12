# Movie Spark ETL (PostgreSQL -> Hive)

This directory contains Spark ETL jobs that load PostgreSQL snapshot tables into Hive ODS partitions (`dt`) and build T+1 offline DWD/DWS layers from those snapshots.

## 1) Prepare config

Copy and edit config:

```bash
cp conf/etl_config.example.json conf/etl_config.json
```

Set PostgreSQL JDBC info and HDFS locations.

`conf/etl_config.json` is a local working file and is intentionally ignored by Git. A local virtual environment under `spark/.venv/` is also ignored.

## Local uv runtime notes

For local execution with `uv`:

- Use Python **3.12** with `pyspark==3.4.2`.
- Run job files by path, for example:

```bash
uv run python jobs/build_ads_hot_movies.py --config conf/etl_config.json --calc-date 2026-02-25
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
- `run_dwd_build.sh` -> `jobs/build_dwd_user_event_wide_di.py`
- `run_dwd_snapshots.sh` -> `jobs/build_dwd_snapshots_di.py`
- `run_dws_build.sh` -> `jobs/build_dws_user_movie_metrics_di.py`
- `run_dws_postgres_interactions.sh` -> `jobs/build_dws_postgres_interactions_1d.py`
- `run_dws_profiles.sh` -> `jobs/build_dws_user_movie_profiles_1d.py`
- `run_ads_hot_movies.sh` -> `jobs/build_ads_hot_movies.py`
- `run_ads_pg_sync.sh` -> `jobs/sync_ads_to_postgres.py`
- `run_ads_itemcf.sh` -> `jobs/build_ads_itemcf_recommendations.py`
- `run_ads_genre_preference.sh` -> `jobs/build_ads_genre_preference_1d.py`
- `run_ads_user_retention.sh` -> `jobs/build_ads_user_retention.py`

## 2) Create Hive ODS tables

Run:

```bash
hive -f ../hive/ods/ods_pg_ddl.hql
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

## 4) Build DWD wide table

Create DWD table first:

```bash
hive -f ../hive/dwd/dwd_ddl.hql
```

Build daily event wide table (`dwd.dwd_user_event_wide_di`) in T+1 offline batch mode by deriving behavior events from PostgreSQL full snapshots and then joining PostgreSQL snapshot dimensions:

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

## 4.5) Generate PostgreSQL-aligned ODS test snapshot

When local business data is too sparse for DWD/DWS validation, you can generate one ODS snapshot partition that still matches the current PostgreSQL-driven pipeline:

- `public.movies` is sampled from the business database only and is never synthetically fabricated.
- User and interaction tables use a mixed strategy: first sample existing PostgreSQL rows, then top up with generated rows that reference the sampled movie set.
- The job writes directly into the same `ods.ods_pg_*_full` Hive partitions consumed by downstream DWD/DWS jobs.

Example:

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --packages org.postgresql:postgresql:42.7.3 \
  jobs/generate_dwd_user_event_source_data.py \
  --config conf/etl_config.json \
  --batch-date 2026-02-25
```

Common overrides:

```bash
--movie-limit 300 \
--user-target 200 \
--new-user-target 40 \
--view-target 5000 \
--comment-target 1000 \
--sample-ratio 0.4 \
--lookback-days 60
```

After it finishes, run the normal jobs with `--snapshot-date` equal to the generated `batch-date`.

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

## 5) Build DWS aggregates

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

## 6) Build ADS hot rankings

Create ADS table:

```bash
hive -f ../hive/ads/ads_ddl.hql
```

Build hot ranking from the configured DWS source.

Recommended source:

- `dws.dws_movie_engagement_daily_1d` with `ads.source_type=movie_metric_daily`

This source is built from PostgreSQL full snapshot tables but filtered by real event timestamps on each `calc-date`, so `DAILY / WEEKLY / MONTHLY` rankings represent true recent 1/7/30 day windows, while `TOTAL` represents the full history accumulated up to `calc-date`.

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

## 7) Build more ADS reports

Two additional ADS jobs are available:

### 8.1 User retention (`ads.ads_user_retention`)

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  jobs/build_ads_user_retention.py \
  --config conf/etl_config.json \
  --calc-date 2026-02-25
```

Retention windows are configurable in `ads_user_retention.retention_days` (default: `[1, 7, 30]`).

### 8.2 Daily genre preference ranking (`ads.ads_genre_preference_1d`)

Default source is `dws.dws_movie_engagement_daily_1d`, which represents daily non-event movie engagement metrics built from PostgreSQL snapshot interactions.

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

## 8) Build ItemCF recommendations (Spark + Hive)

Generate both outputs in one run.

Recommended source:

- `dws.dws_user_item_preference_1d` with `ads_itemcf.source_type=user_item_preference`

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
- `event_score_weights`: behavior-to-preference scoring weights (`favorite_add` / `favorite_remove`; `favorite` is still accepted as a fallback for add weight)

## DWD event derivation

`dwd.dwd_user_event_wide_di` is now built fully offline from PostgreSQL ODS full snapshots.

Daily events are reconstructed by filtering business timestamps to `--calc-date`:

- `view_history`: `ods.ods_pg_view_history_full.view_time`
- `rating`: `ods.ods_pg_ratings_full.rating_time`
- `comment`: `ods.ods_pg_comments_full.comment_time`
- `comment_like`: `ods.ods_pg_comment_likes_full.create_time`
- `favorite`: `ods.ods_pg_favorites_full.create_time`
- `watched`: `ods.ods_pg_watched_movies_full.create_time`
- `user_register`: `ods.ods_pg_users_full.create_time`
- `favorite_folder_action`: `ods.ods_pg_favorite_folders_full.create_time` / `update_time`

For compatibility with the existing DWD schema, optional session fields such as `session_id` and `page_url` are retained but will be null in PostgreSQL-only T+1 mode. Fields that cannot be reconstructed from snapshots, such as `search` and `user_login`, naturally remain absent.

## Suggested scheduling

- Daily PostgreSQL ODS snapshot: run once per day (for example 01:30) with `--batch-date`.
- DWD build: run after PostgreSQL ODS full partitions are ready, typically as T+1 for `--calc-date`.
- DWS build: run after DWD partition is generated (for example hourly or daily T+0 refresh).
- ADS hot rankings: run after DWS daily movie metrics are generated (daily or rolling refresh by `--calc-date`).
