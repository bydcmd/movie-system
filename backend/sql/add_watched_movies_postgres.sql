-- 创建用户"看过"记录表 (PostgreSQL 版本)
CREATE TABLE IF NOT EXISTS watched_movies (
  user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
  movie_id BIGINT NOT NULL COMMENT '电影ID',
  create_time TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP COMMENT '看过标记时间',
  PRIMARY KEY (user_id, movie_id),
  CONSTRAINT fk_watched_movies_movie_id FOREIGN KEY (movie_id) REFERENCES movies(movie_id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_watched_user_id ON watched_movies(user_id);
CREATE INDEX IF NOT EXISTS idx_watched_movie_id ON watched_movies(movie_id);

-- 添加表注释
COMMENT ON TABLE watched_movies IS '用户看过记录';
COMMENT ON COLUMN watched_movies.user_id IS '用户ID';
COMMENT ON COLUMN watched_movies.movie_id IS '电影ID';
COMMENT ON COLUMN watched_movies.create_time IS '看过标记时间';
