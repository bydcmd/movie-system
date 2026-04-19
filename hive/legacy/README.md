# Legacy Hive DDLs

This directory contains the archived layered Hive warehouse DDLs:

- `ods/ods_pg_ddl.hql`
- `dwd/dwd_ddl.hql`
- `dws/dws_ddl.hql`
- `ads/ads_ddl.hql`

They are kept only for reference and rollback during the compact warehouse migration.

The active Hive schema now lives under `hive/compact/` and keeps only:

- `dw.dw_user_event_fact_di`
- `dm.dm_hot_movies`
- `dm.dm_user_retention`
- `dm.dm_genre_preference_1d`
