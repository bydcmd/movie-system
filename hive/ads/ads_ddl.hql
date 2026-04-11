CREATE DATABASE IF NOT EXISTS ads;
USE ads;

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_hot_movies (
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
LOCATION '/warehouse/movie/ads/hot_movies'
TBLPROPERTIES ('orc.compress'='SNAPPY');


CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_user_retention (
  cohort_dt string,
  retention_day int,
  cohort_users bigint,
  retained_users bigint,
  retention_rate decimal(10,4)
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/user_retention'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_genre_preference_1d (
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
LOCATION '/warehouse/movie/ads/genre_preference_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_itemcf_similar_movies (
  movie_id bigint,
  similar_movie_id bigint,
  rank_no int,
  similarity_score decimal(18,8),
  common_user_cnt bigint,
  movie_user_cnt bigint,
  similar_movie_user_cnt bigint,
  similarity_type tinyint
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/itemcf_similar_movies'
TBLPROPERTIES ('orc.compress'='SNAPPY');
