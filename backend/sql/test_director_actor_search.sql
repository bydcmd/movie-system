-- 测试导演和演员筛选功能
-- 请先确保数据库中有测试数据

-- 1. 查看几条包含导演和演员信息的电影
SELECT movie_id, name, directors, actors
FROM movies
WHERE directors IS NOT NULL AND actors IS NOT NULL
LIMIT 5;

-- 2. 查看导演字段的 JSON 结构（分析数据格式）
SELECT 
    movie_id, 
    name, 
    directors,
    JSON_TYPE(directors) as director_json_type,
    JSON_LENGTH(directors) as director_count
FROM movies
WHERE directors IS NOT NULL
LIMIT 3;

-- 3. 查看演员字段的 JSON 结构
SELECT 
    movie_id, 
    name, 
    actors,
    JSON_TYPE(actors) as actor_json_type,
    JSON_LENGTH(actors) as actor_count
FROM movies
WHERE actors IS NOT NULL
LIMIT 3;

-- 4. 提取所有导演名字（用于了解实际存储的格式）
SELECT DISTINCT 
    JSON_UNQUOTE(JSON_EXTRACT(directors, '$[0].name')) as director_name_0,
    JSON_UNQUOTE(JSON_EXTRACT(directors, '$[0].id')) as director_id_0
FROM movies
WHERE directors IS NOT NULL AND JSON_LENGTH(directors) > 0
LIMIT 20;

-- 5. 提取所有演员名字
SELECT DISTINCT 
    JSON_UNQUOTE(JSON_EXTRACT(actors, '$[0].name')) as actor_name_0,
    JSON_UNQUOTE(JSON_EXTRACT(actors, '$[0].id')) as actor_id_0
FROM movies
WHERE actors IS NOT NULL AND JSON_LENGTH(actors) > 0
LIMIT 20;

-- 6. 测试 JSON_SEARCH（模糊匹配）
-- 使用 % 通配符进行模糊匹配
SELECT movie_id, name, directors, douban_score, year
FROM movies
WHERE JSON_SEARCH(directors, 'one', '%诺兰%', NULL, '$[*].name') IS NOT NULL;

-- 7. 测试 JSON_SEARCH（演员模糊匹配）
SELECT movie_id, name, actors, douban_score, year
FROM movies
WHERE JSON_SEARCH(actors, 'one', '%莱昂纳多%', NULL, '$[*].name') IS NOT NULL;

-- 8. 如果 JSON_SEARCH 返回空，尝试使用 JSON_CONTAINS 配合特定结构
-- 这种方式需要知道具体的 JSON 结构
SELECT movie_id, name, directors, douban_score, year
FROM movies
WHERE JSON_CONTAINS(directors, JSON_OBJECT('name', '克里斯托弗·诺兰'));

-- 9. 更通用的模糊匹配方式（使用 LIKE）
-- 这种方法适用于任何文本内容
SELECT movie_id, name, directors, douban_score, year
FROM movies
WHERE directors LIKE '%诺兰%';

-- 10. 演员 LIKE 模糊匹配
SELECT movie_id, name, actors, douban_score, year
FROM movies
WHERE actors LIKE '%迪卡普里奥%';

-- 11. 查看 movies 表中 directors 和 actors 为 NULL 或空的情况
SELECT 
    COUNT(*) as total,
    COUNT(directors) as has_directors,
    COUNT(actors) as has_actors,
    COUNT(CASE WHEN directors = '[]' THEN 1 END) as empty_directors,
    COUNT(CASE WHEN actors = '[]' THEN 1 END) as empty_actors
FROM movies;

-- 12. 如果数据是英文存储的，尝试英文搜索
-- 诺兰可能是 "Christopher Nolan"
SELECT movie_id, name, directors, douban_score, year
FROM movies
WHERE JSON_SEARCH(directors, 'one', '%Nolan%', NULL, '$[*].name') IS NOT NULL
   OR directors LIKE '%Nolan%';

-- 莱昂纳多可能是 "Leonardo DiCaprio"
SELECT movie_id, name, actors, douban_score, year
FROM movies
WHERE JSON_SEARCH(actors, 'one', '%DiCaprio%', NULL, '$[*].name') IS NOT NULL
   OR actors LIKE '%DiCaprio%';
