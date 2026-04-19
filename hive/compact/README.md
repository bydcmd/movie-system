# Hive 4-table warehouse

Current compact Hive warehouse keeps only four tables:

- `dw.dw_user_event_fact_di`
- `dm.dm_hot_movies`
- `dm.dm_user_retention`
- `dm.dm_genre_preference_1d`

Apply the compact schema:

```bash
hive -f hive/compact/compact_ddl.hql
```

After Spark jobs are migrated and data is verified, drop the legacy layered tables:

```bash
hive -f hive/compact/drop_legacy_tables.hql
```

The old ODS/DWD/DWS/ADS DDL files are archived under `hive/legacy/` for rollback and diffing.
