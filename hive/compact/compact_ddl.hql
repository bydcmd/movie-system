CREATE DATABASE IF NOT EXISTS dw;
CREATE DATABASE IF NOT EXISTS dm;

CREATE EXTERNAL TABLE IF NOT EXISTS dw.dw_user_event_fact_di (
  topic string,
  event_key string,
  event_id string,
  event_type string,
  event_ts timestamp,
  occurred_at timestamp,
  ingest_time timestamp,
  hh string,
  user_id string,
  user_nickname string,
  user_role tinyint,
  user_status int,
  movie_id bigint,
  movie_name string,
  movie_year int,
  movie_genres string,
  movie_score decimal(3,1),
  movie_douban_score decimal(3,1),
  comment_id bigint,
  comment_type tinyint,
  comment_votes int,
  comment_time timestamp,
  comment_title string,
  comment_content_length int,
  folder_id bigint,
  folder_name string,
  folder_is_public tinyint,
  operation string,
  operation_norm string,
  rating int,
  rating_snapshot int,
  rating_time timestamp,
  search_keyword string,
  result_count bigint,
  filter_conditions string,
  search_time bigint,
  is_view tinyint,
  is_rating tinyint,
  is_comment tinyint,
  is_comment_like tinyint,
  is_favorite tinyint,
  is_watched tinyint,
  is_search tinyint,
  is_register tinyint,
  is_login tinyint,
  is_favorite_folder_action tinyint,
  event_data string,
  raw_json string,
  session_id string,
  page_url string,
  sequence_number int,
  client_timestamp bigint,
  entry_url string,
  referrer string,
  user_agent string,
  device_type string,
  session_start_time bigint
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dw/user_event_fact_di'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dm.dm_hot_movies (
  movie_id bigint,
  movie_name string,
  movie_year int,
  movie_genres string,
  movie_score decimal(3,1),
  movie_douban_score decimal(3,1),
  period_type string,
  rank_no int,
  hot_score decimal(18,4),
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
  window_start string,
  window_end string,
  last_event_ts timestamp
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
