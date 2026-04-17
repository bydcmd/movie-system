CREATE DATABASE IF NOT EXISTS dws;
USE dws;

DROP TABLE IF EXISTS dws.dws_user_action_1d;
DROP TABLE IF EXISTS dws.dws_movie_action_1d;
DROP TABLE IF EXISTS dws.dws_event_type_1d;
DROP TABLE IF EXISTS dws.dws_user_profile_1d;
DROP TABLE IF EXISTS dws.dws_movie_profile_1d;

CREATE EXTERNAL TABLE IF NOT EXISTS dws.dws_user_item_preference_1d (
  user_id string,
  movie_id bigint,
  view_cnt bigint,
  rating_cnt bigint,
  rating_avg decimal(10,2),
  comment_cnt bigint,
  favorite_add_cnt bigint,
  favorite_remove_cnt bigint,
  watched_cnt bigint,
  preference_score decimal(18,8),
  last_event_ts timestamp,
  source_snapshot_dt string
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dws/user_item_preference_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dws.dws_movie_engagement_1d (
  movie_id bigint,
  movie_name string,
  movie_year int,
  movie_genres string,
  movie_score decimal(3,1),
  movie_douban_score decimal(3,1),
  view_pv bigint,
  view_uv bigint,
  rating_cnt bigint,
  rating_avg decimal(10,2),
  comment_cnt bigint,
  comment_like_cnt bigint,
  favorite_add_cnt bigint,
  favorite_remove_cnt bigint,
  watched_cnt bigint,
  active_user_cnt bigint,
  hot_score decimal(18,4),
  last_event_ts timestamp,
  source_snapshot_dt string
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dws/movie_engagement_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dws.dws_movie_engagement_daily_1d (
  movie_id bigint,
  movie_name string,
  movie_year int,
  movie_genres string,
  movie_score decimal(3,1),
  movie_douban_score decimal(3,1),
  view_pv bigint,
  view_uv bigint,
  rating_cnt bigint,
  rating_avg decimal(10,2),
  comment_cnt bigint,
  comment_like_cnt bigint,
  favorite_add_cnt bigint,
  favorite_remove_cnt bigint,
  watched_cnt bigint,
  active_user_cnt bigint,
  hot_score decimal(18,4),
  last_event_ts timestamp,
  source_snapshot_dt string
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dws/movie_engagement_daily_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');
