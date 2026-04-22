# Hive 4-table warehouse

Current compact Hive warehouse keeps only four tables:

- `dw.dw_user_event_fact_di`
- `dm.dm_hot_movies`
- `dm.dm_user_retention`
- `dm.dm_genre_preference_1d`

The compact `dw.dw_user_event_fact_di` keeps only the columns required by the active DM jobs.

Apply the compact schema:

```bash
hive -f hive/compact/compact_ddl.hql
```

