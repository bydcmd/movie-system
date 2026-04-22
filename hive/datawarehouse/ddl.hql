CREATE DATABASE IF NOT EXISTS dw;
CREATE DATABASE IF NOT EXISTS dm;

CREATE EXTERNAL TABLE IF NOT EXISTS dw.dw_user_event_fact_di (
  event_ts timestamp,
  user_id string,
  movie_id bigint,
  movie_genres string,
  is_view tinyint,
  is_rating tinyint,
  is_comment tinyint,
  is_comment_like tinyint,
  is_favorite tinyint,
  is_watched tinyint,
  is_register tinyint
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dw/user_event_fact_di'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dm.dm_hot_movies (
  movie_id bigint,
  period_type string,
  hot_score decimal(18,4),
  view_pv bigint,
  rating_cnt bigint,
  watched_cnt bigint
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dm/hot_movies'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dm.dm_user_retention (
  cohort_dt string,
  retention_day int,
  cohort_users bigint,
  retained_users bigint,
  retention_rate decimal(10,4)
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dm/user_retention'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dm.dm_genre_preference_1d (
  genre string,
  rank_no int,
  movie_cnt bigint,
  view_pv bigint,
  view_uv bigint,
  rating_cnt bigint,
  watched_cnt bigint,
  hot_score_sum decimal(18,4)
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dm/genre_preference_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');
