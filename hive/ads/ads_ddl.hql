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

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_hybrid_hot_movies (
  movie_id bigint,
  movie_name string,
  movie_year int,
  movie_genres string,
  movie_score decimal(3,1),
  movie_douban_score decimal(3,1),
  period_type string,
  rank_no int,
  hybrid_hot_score decimal(18,8),
  base_hot_score decimal(18,4),
  recent_hot_score decimal(18,4),
  recent_trend_boost decimal(18,8),
  trend_ratio_boost decimal(18,8),
  quality_boost decimal(18,8),
  recent_view_pv bigint,
  recent_view_uv bigint,
  recent_rating_cnt bigint,
  recent_rating_avg decimal(10,2),
  recent_comment_cnt bigint,
  recent_comment_like_cnt bigint,
  recent_favorite_add_cnt bigint,
  recent_favorite_remove_cnt bigint,
  recent_watched_cnt bigint,
  recent_active_user_cnt bigint,
  window_start string,
  window_end string,
  base_last_event_ts timestamp,
  recent_last_event_ts timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/hybrid_hot_movies'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_user_funnel_1d (
  total_active_users bigint,
  view_users bigint,
  rating_users bigint,
  comment_users bigint,
  favorite_users bigint,
  favorite_folder_action_users bigint,
  watched_users bigint,
  view_to_rating_rate decimal(10,4),
  rating_to_comment_rate decimal(10,4),
  comment_to_favorite_rate decimal(10,4),
  favorite_to_watched_rate decimal(10,4)
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/user_funnel_1d'
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

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_itemcf_user_recommendations (
  user_id string,
  movie_id bigint,
  rank_no int,
  recommend_score decimal(18,8),
  seed_item_cnt bigint,
  best_similarity decimal(18,8)
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/itemcf_user_recommendations'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_reco_user_item_features_1d (
  user_id string,
  movie_id bigint,
  feature_score decimal(18,8),
  view_cnt bigint,
  rating_cnt bigint,
  comment_cnt bigint,
  favorite_add_cnt bigint,
  favorite_remove_cnt bigint,
  watched_cnt bigint,
  last_event_ts timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/reco_user_item_features_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_hybrid_user_recommendations (
  user_id string,
  movie_id bigint,
  rank_no int,
  base_rank_no int,
  rerank_score decimal(18,8),
  base_recommend_score decimal(18,8),
  genre_match_score decimal(18,8),
  popularity_boost decimal(18,8),
  quality_boost decimal(18,8),
  recent_interaction_penalty decimal(18,8),
  seed_item_cnt bigint,
  best_similarity decimal(18,8),
  movie_name string,
  movie_year int,
  movie_genres string,
  movie_score decimal(3,1),
  movie_douban_score decimal(3,1),
  movie_hot_score decimal(18,4),
  is_recently_interacted tinyint,
  last_recent_event_ts timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/hybrid_user_recommendations'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_search_keyword_insights_1d (
  search_keyword string,
  rank_no int,
  search_cnt bigint,
  search_user_cnt bigint,
  zero_result_cnt bigint,
  zero_result_rate decimal(10,4),
  avg_result_count decimal(10,2),
  after_search_view_user_cnt bigint,
  after_search_watch_user_cnt bigint,
  after_search_rating_user_cnt bigint,
  search_to_view_rate decimal(10,4),
  view_to_watch_rate decimal(10,4),
  problem_score decimal(10,4)
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/search_keyword_insights_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_search_funnel_1d (
  search_user_cnt bigint,
  search_cnt bigint,
  search_with_result_cnt bigint,
  search_zero_result_cnt bigint,
  after_search_view_user_cnt bigint,
  after_search_rating_user_cnt bigint,
  after_search_favorite_user_cnt bigint,
  after_search_watched_user_cnt bigint,
  search_to_view_rate decimal(10,4),
  view_to_watched_rate decimal(10,4),
  search_to_rating_rate decimal(10,4)
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/search_funnel_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');
