CREATE DATABASE IF NOT EXISTS dws;
USE dws;

CREATE EXTERNAL TABLE IF NOT EXISTS dws.dws_user_action_1d (
  user_id string,
  user_nickname string,
  user_role tinyint,
  user_status int,
  view_cnt bigint,
  rating_cnt bigint,
  rating_avg decimal(10,2),
  comment_cnt bigint,
  comment_like_cnt bigint,
  favorite_add_cnt bigint,
  favorite_remove_cnt bigint,
  watched_cnt bigint,
  search_cnt bigint,
  login_cnt bigint,
  register_cnt bigint,
  active_movie_cnt bigint,
  last_event_ts timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dws/user_action_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dws.dws_movie_action_1d (
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
  last_event_ts timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dws/movie_action_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dws.dws_event_type_1d (
  event_type string,
  event_cnt bigint,
  user_cnt bigint,
  movie_cnt bigint,
  rating_avg decimal(10,2),
  last_event_ts timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dws/event_type_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');
