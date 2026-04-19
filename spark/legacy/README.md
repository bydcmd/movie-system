# Legacy Spark files

This directory contains the archived layered Spark pipeline that used ODS/DWD/DWS/ADS Hive tables.

These files are kept only for reference during the compact warehouse migration.

- `jobs/`: old layered ETL jobs
- `scripts/`: old wrapper and validation scripts

The active pipeline now lives directly under `spark/` and targets:

- `dw.dw_user_event_fact_di`
- `dm.dm_hot_movies`
- `dm.dm_user_retention`
- `dm.dm_genre_preference_1d`
