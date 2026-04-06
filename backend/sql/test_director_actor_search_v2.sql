-- 测试导演和演员筛选功能 V2（支持模糊匹配）

-- 1. 查看几条包含导演和演员信息的电影样本
SELECT movie_id, name, directors, actors
FROM movies
WHERE directors IS NOT NULL AND actors IS NOT NULL
LIMIT 5;

-- 2. 查看导演字段的 JSON 结构
SELECT 
    movie_id, 
    name, 
    directors,
    JSON_TYPE(directors) as json_type,
    JSON_LENGTH(directors) as count
FROM movies
WHERE directors IS NOT NULL AND JSON_LENGTH(directors) > 0
LIMIT 3;

-- 3. 提取所有不同的导演名字（用于了解实际存储的格式）
-- 注意：这里只提取第一个导演的名字
SELECT DISTINCT 
    JSON_UNQUOTE(JSON_EXTRACT(directors, '$[0].NAME')) as director_name
FROM movies
WHERE directors IS NOT NULL 
  AND JSON_LENGTH(directors) > 0
  AND directors != '[]'
  AND directors != ''
LIMIT 30;

-- 4. 提取所有不同的演员名字（前3个演员）
SELECT DISTINCT 
    JSON_UNQUOTE(JSON_EXTRACT(actors, '$[0].NAME')) as actor_1,
    JSON_UNQUOTE(JSON_EXTRACT(actors, '$[1].NAME')) as actor_2,
    JSON_UNQUOTE(JSON_EXTRACT(actors, '$[2].NAME')) as actor_3
FROM movies
WHERE actors IS NOT NULL 
  AND JSON_LENGTH(actors) > 0
LIMIT 30;

-- 5. 使用模糊匹配搜索导演（推荐方式）
-- 匹配名字中包含"诺兰"或"Nolan"的导演
SELECT movie_id, name, directors, douban_score, year
FROM movies
WHERE JSON_SEARCH(directors, 'one', '%诺兰%', NULL, '$[*].NAME') IS NOT NULL
   OR JSON_SEARCH(directors, 'one', '%Nolan%', NULL, '$[*].NAME') IS NOT NULL
   OR directors LIKE '%诺兰%'
   OR directors LIKE '%Nolan%';

-- 6. 使用模糊匹配搜索演员
-- 匹配名字中包含"昂纳多"或"Caprio"的演员
SELECT movie_id, name, actors, douban_score, year
FROM movies
WHERE JSON_SEARCH(actors, 'one', '%昂纳多%', NULL, '$[*].NAME') IS NOT NULL
   OR JSON_SEARCH(actors, 'one', '%Caprio%', NULL, '$[*].NAME') IS NOT NULL
   OR actors LIKE '%昂纳多%'
   OR actors LIKE '%Caprio%';

-- 7. 组合搜索：导演 + 类型 + 评分
-- 查找诺兰导演的科幻电影，评分大于8分
SELECT m.movie_id, m.name, m.douban_score, m.year, m.genres, m.directors
FROM movies m
WHERE (
    JSON_SEARCH(m.directors, 'one', '%诺兰%', NULL, '$[*].NAME') IS NOT NULL
    OR JSON_SEARCH(m.directors, 'one', '%Nolan%', NULL, '$[*].NAME') IS NOT NULL
    OR m.directors LIKE '%诺兰%'
)
  AND m.douban_score >= 8.0
  AND EXISTS (
      SELECT 1
      FROM movie_genre_relation mgr
      INNER JOIN genres g ON mgr.genre_id = g.id
      WHERE mgr.movie_id = m.movie_id AND g.name = '科幻'
  );

-- 8. 使用 CONCAT 构建动态模糊匹配（这是 MyBatis 实际使用的语法）
-- 模拟传入参数 '诺兰'
SET @director_name = '诺兰';
SELECT movie_id, name, directors, douban_score, year
FROM movies
WHERE JSON_SEARCH(directors, 'one', CONCAT('%', @director_name, '%'), NULL, '$[*].NAME') IS NOT NULL
   OR directors LIKE CONCAT('%', @director_name, '%');

-- 9. 统计数据
SELECT 
    COUNT(*) as total_movies,
    COUNT(directors) as has_directors_field,
    COUNT(actors) as has_actors_field,
    SUM(CASE WHEN directors IS NOT NULL AND directors != '[]' AND directors != '' THEN 1 ELSE 0 END) as has_director_data,
    SUM(CASE WHEN actors IS NOT NULL AND actors != '[]' AND actors != '' THEN 1 ELSE 0 END) as has_actor_data
FROM movies;

-- 10. 查看 NULL 和空值情况
SELECT 
    SUM(CASE WHEN directors IS NULL THEN 1 ELSE 0 END) as null_directors,
    SUM(CASE WHEN directors = '[]' THEN 1 ELSE 0 END) as empty_array_directors,
    SUM(CASE WHEN directors = '' THEN 1 ELSE 0 END) as empty_string_directors,
    SUM(CASE WHEN actors IS NULL THEN 1 ELSE 0 END) as null_actors,
    SUM(CASE WHEN actors = '[]' THEN 1 ELSE 0 END) as empty_array_actors,
    SUM(CASE WHEN actors = '' THEN 1 ELSE 0 END) as empty_string_actors
FROM movies;
