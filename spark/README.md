# Movie Spark ETL

Spark now targets the compact 4-table Hive warehouse:

- `dw.dw_user_event_fact_di`
- `dm.dm_hot_movies`
- `dm.dm_user_retention`
- `dm.dm_genre_preference_1d`

The compact event fact table retains only the fields consumed by the active DM pipeline and excludes favorite-folder management actions.

The old ODS/DWD/DWS/ADS layered flow and ItemCF recommendation job are no longer part of the default pipeline.

## Prepare config

```bash
cp conf/etl_config.example.json conf/etl_config.json
```

Set PostgreSQL JDBC info and HDFS locations in `conf/etl_config.json`.
Active config keys now use compact naming such as `dw_event_fact`, `dm_hot_movies`, `dm_user_retention`, and `dm_genre_preference`.

## Create Hive tables

From the repository root:

```bash
hive -f hive/compact/compact_ddl.hql
```

## Run compact ETL

```bash
bash etl.sh 2026-02-25 conf/etl_config.json
```

Pipeline order:

1. Build `dw.dw_user_event_fact_di` directly from PostgreSQL source tables.
2. Build `dm.dm_hot_movies` from the compact event fact table.
3. Build `dm.dm_user_retention` from the compact event fact table.
4. Build `dm.dm_genre_preference_1d` from `dm.dm_hot_movies` and the compact event fact table, with genre-level `view_uv` counted from distinct users in event facts.
5. Sync the three DM result tables back to PostgreSQL statistics tables.

Optional source data generation helper:

```bash
bash run_dw_source_data.sh 2026-02-25 conf/etl_config.json
```

## Run jobs separately

```bash
bash run_dw_user_event_fact.sh 2026-02-25 conf/etl_config.json
bash run_dm_hot_movies.sh 2026-02-25 conf/etl_config.json
bash run_dm_user_retention.sh 2026-02-25 conf/etl_config.json
bash run_dm_genre_preference.sh 2026-02-25 conf/etl_config.json
bash run_dm_pg_sync.sh 2026-02-25 conf/etl_config.json
```

## Sync options

`run_dm_pg_sync.sh` supports these sync types:

- `hot_movies`
- `user_retention`
- `genre_preference`
- `all`

Example:

```bash
bash run_dm_pg_sync.sh 2026-02-25 conf/etl_config.json --sync-types hot_movies,user_retention

## Validate results

```bash
bash validate_etl_results.sh 2026-02-25
```

Skip PostgreSQL checks when needed:

```bash
bash validate_etl_results.sh --calc-date 2026-02-25 --skip-pg
```
```
