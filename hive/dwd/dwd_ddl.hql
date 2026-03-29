CREATE DATABASE IF NOT EXISTS dwd;
USE dwd;

CREATE EXTERNAL TABLE IF NOT EXISTS dwd.dwd_user_event_wide_di (
  topic string,
  event_key string,
  event_id string,
  event_type string,
  event_ts timestamp,
  kafka_timestamp timestamp,
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
  raw_json string
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dwd/user_event_wide_di'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dwd.dwd_user_snapshot_di (
  user_id string,
  user_nickname string,
  user_avatar string,
  user_url string,
  user_role tinyint,
  user_status int,
  password_version int,
  email string,
  create_time timestamp,
  update_time timestamp,
  register_date string,
  update_date string,
  source_snapshot_dt string
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dwd/user_snapshot_di'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS dwd.dwd_movie_snapshot_di (
  movie_id bigint,
  movie_name string,
  movie_alias string,
  movie_actors string,
  movie_cover string,
  movie_directors string,
  movie_douban_score decimal(3,1),
  movie_score decimal(3,1),
  movie_douban_votes int,
  movie_votes int,
  movie_genres string,
  imdb_id string,
  movie_languages string,
  movie_duration_mins string,
  movie_regions string,
  release_date string,
  storyline string,
  movie_year int,
  movie_writers string,
  rating_weights string,
  full_search_text string,
  source_snapshot_dt string
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/dwd/movie_snapshot_di'
TBLPROPERTIES ('orc.compress'='SNAPPY');
