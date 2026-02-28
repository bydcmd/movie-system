CREATE DATABASE IF NOT EXISTS ods;
USE ods;

-- PostgreSQL snapshots
CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_comment_likes_full (
  id bigint,
  comment_id bigint,
  user_id string,
  create_time timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/comment_likes'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_comments_full (
  comment_id bigint,
  user_id string,
  movie_id bigint,
  content string,
  votes int,
  comment_time timestamp,
  title string,
  type tinyint,
  version int
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/comments'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_favorite_folders_full (
  id bigint,
  user_id string,
  name string,
  description string,
  is_public tinyint,
  movie_count int,
  create_time timestamp,
  update_time timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/favorite_folders'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_favorites_full (
  user_id string,
  movie_id bigint,
  folder_id bigint,
  create_time timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/favorites'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_movies_full (
  movie_id bigint,
  name string,
  alias string,
  actors string,
  cover string,
  directors string,
  douban_score decimal(3,1),
  score decimal(3,1),
  douban_votes int,
  votes int,
  genres string,
  imdb_id string,
  languages string,
  mins string,
  regions string,
  release_date string,
  storyline string,
  year int,
  writers string,
  rating_weights string,
  full_search_text string
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/movies'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_ratings_full (
  user_id string,
  movie_id bigint,
  rating int,
  rating_time timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/ratings'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_users_full (
  user_id string,
  user_nickname string,
  user_password string,
  user_avatar string,
  user_url string,
  role tinyint,
  status int,
  password_version int,
  email string,
  create_time timestamp,
  update_time timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/users'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_view_history_full (
  history_id bigint,
  user_id string,
  movie_id bigint,
  view_time timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/view_history'
TBLPROPERTIES ('orc.compress'='SNAPPY');

CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_pg_watched_movies_full (
  user_id string,
  movie_id bigint,
  create_time timestamp
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/postgres/watched_movies'
TBLPROPERTIES ('orc.compress'='SNAPPY');

-- Kafka event logs
CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_kafka_event_log_di (
  topic string,
  event_key string,
  kafka_timestamp timestamp,
  event_id string,
  event_type string,
  occurred_at timestamp,
  user_id string,
  movie_id bigint,
  comment_id bigint,
  folder_id bigint,
  operation string,
  rating int,
  search_keyword string,
  result_count bigint,
  event_data string,
  raw_json string,
  ingest_time timestamp
)
PARTITIONED BY (dt string, hh string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/kafka/event_log'
TBLPROPERTIES ('orc.compress'='SNAPPY');
