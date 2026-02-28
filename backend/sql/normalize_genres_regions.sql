/*
 Database Normalization Script: Genres, Regions and Languages
 
 Purpose: Refactor movies table to normalize genres, regions and languages relationships
- Create separate genres, regions and languages tables
 - Create junction tables for many-to-many relationships
 - Migrate existing data from comma-separated fields to normalized structure
 
 Date: February 8, 2026
*/
-- ----------------------------
-- 1. 迁移 Genres 数据
-- ----------------------------

-- 插入唯一的 genres
-- 说明: 使用 string_to_array 将字符串转为数组，再用 unnest 展开成行
INSERT INTO genres (name)
SELECT DISTINCT TRIM(g_name)
FROM movies,
     unnest(string_to_array(genres, '/')) AS g_name
WHERE genres IS NOT NULL
  AND TRIM(g_name) <> ''
    ON CONFLICT (name) DO NOTHING;
-- 注意: 如果你需要完全模拟 MySQL 的 Update 行为，可以使用 DO UPDATE SET name = EXCLUDED.name，但通常 DO NOTHING 效率更高且效果一致

-- 填充 movie_genre_relation 表
INSERT INTO movie_genre_relation (movie_id, genre_id)
SELECT DISTINCT m.movie_id, g.id
FROM movies m,
     unnest(string_to_array(m.genres, '/')) AS g_name
         JOIN genres g ON g.name = TRIM(g_name)
WHERE m.genres IS NOT NULL
  AND TRIM(g_name) <> ''
    ON CONFLICT (movie_id, genre_id) DO NOTHING;


-- ----------------------------
-- 2. 迁移 Regions 数据
-- ----------------------------

-- 说明:
-- 1) 兼容中英混合值（如: "印度 India"）
-- 2) 处理常见英文别名（如: "Italy", "Kazakhstan"）
-- 3) 过滤明显无效值（如: "BBC"）
-- 4) 对历史已写入的别名数据做关系迁移与清理

WITH raw_regions AS (
    SELECT m.movie_id,
           NULLIF(TRIM(regexp_replace(r_name, '\s+', ' ', 'g')), '') AS raw_name
    FROM movies m
             CROSS JOIN LATERAL unnest(string_to_array(m.regions, '/')) AS r_name
    WHERE m.regions IS NOT NULL
),
alias_map(alias_name, canonical_name, canonical_en, is_valid) AS (
    VALUES
        ('USA', '美国', 'United States', TRUE),
        ('U.S.A.', '美国', 'United States', TRUE),
        ('United States', '美国', 'United States', TRUE),
        ('UK', '英国', 'United Kingdom', TRUE),
        ('United Kingdom', '英国', 'United Kingdom', TRUE),
        ('China', '中国', 'China', TRUE),
        ('Mainland China', '中国大陆', 'China Mainland', TRUE),
        ('Hong Kong', '中国香港', 'Hong Kong, China', TRUE),
        ('Taiwan', '中国台湾', 'Taiwan, China', TRUE),
        ('South Korea', '韩国', 'South Korea', TRUE),
        ('Korea', '韩国', 'South Korea', TRUE),
        ('Japan', '日本', 'Japan', TRUE),
        ('France', '法国', 'France', TRUE),
        ('Germany', '德国', 'Germany', TRUE),
        ('Spain', '西班牙', 'Spain', TRUE),
        ('Russia', '俄罗斯', 'Russia', TRUE),
        ('Canada', '加拿大', 'Canada', TRUE),
        ('Australia', '澳大利亚', 'Australia', TRUE),
        ('Italy', '意大利', 'Italy', TRUE),
        ('Kazakhstan', '哈萨克斯坦', 'Kazakhstan', TRUE),
        ('India', '印度', 'India', TRUE),
        ('Poland', '波兰', 'Poland', TRUE),
        ('印度 India', '印度', 'India', TRUE),
        ('BBC', NULL::varchar, NULL::varchar, FALSE)
),
normalized_regions AS (
    SELECT
        rr.movie_id,
        rr.raw_name,
        CASE
            WHEN am.is_valid = FALSE THEN NULL
            WHEN am.canonical_name IS NOT NULL THEN am.canonical_name
            WHEN rr.raw_name ~ '[A-Za-z]' AND rr.raw_name ~ '[^[:ascii:]]'
                THEN NULLIF(TRIM(regexp_replace(rr.raw_name, '\s+[A-Za-z][A-Za-z .-]*$', '')), '')
            ELSE rr.raw_name
            END AS canonical_name,
        CASE
            WHEN am.is_valid = FALSE THEN NULL
            WHEN am.canonical_en IS NOT NULL THEN am.canonical_en
            WHEN rr.raw_name ~ '[A-Za-z]' AND rr.raw_name ~ '[^[:ascii:]]'
                THEN NULLIF(TRIM((regexp_match(rr.raw_name, '([A-Za-z][A-Za-z .-]*)$'))[1]), '')
            WHEN rr.raw_name ~ '^[A-Za-z][A-Za-z .-]*$' THEN rr.raw_name
            ELSE NULL
            END AS canonical_en
    FROM raw_regions rr
             LEFT JOIN alias_map am ON lower(rr.raw_name) = lower(am.alias_name)
    WHERE rr.raw_name IS NOT NULL
),
region_seed AS (
    SELECT canonical_name AS name,
           MAX(canonical_en) FILTER (WHERE canonical_en IS NOT NULL AND canonical_en <> '') AS name_en
    FROM normalized_regions
    WHERE canonical_name IS NOT NULL
      AND canonical_name <> ''
    GROUP BY canonical_name
)
INSERT INTO regions (name, name_en)
SELECT rs.name, rs.name_en
FROM region_seed rs
ON CONFLICT (name) DO UPDATE
    SET name_en = COALESCE(regions.name_en, EXCLUDED.name_en);

-- 填充 movie_region_relation 表
WITH raw_regions AS (
    SELECT m.movie_id,
           NULLIF(TRIM(regexp_replace(r_name, '\s+', ' ', 'g')), '') AS raw_name
    FROM movies m
             CROSS JOIN LATERAL unnest(string_to_array(m.regions, '/')) AS r_name
    WHERE m.regions IS NOT NULL
),
alias_map(alias_name, canonical_name, canonical_en, is_valid) AS (
    VALUES
        ('USA', '美国', 'United States', TRUE),
        ('U.S.A.', '美国', 'United States', TRUE),
        ('United States', '美国', 'United States', TRUE),
        ('UK', '英国', 'United Kingdom', TRUE),
        ('United Kingdom', '英国', 'United Kingdom', TRUE),
        ('China', '中国', 'China', TRUE),
        ('Mainland China', '中国大陆', 'China Mainland', TRUE),
        ('Hong Kong', '中国香港', 'Hong Kong, China', TRUE),
        ('Taiwan', '中国台湾', 'Taiwan, China', TRUE),
        ('South Korea', '韩国', 'South Korea', TRUE),
        ('Korea', '韩国', 'South Korea', TRUE),
        ('Japan', '日本', 'Japan', TRUE),
        ('France', '法国', 'France', TRUE),
        ('Germany', '德国', 'Germany', TRUE),
        ('Spain', '西班牙', 'Spain', TRUE),
        ('Russia', '俄罗斯', 'Russia', TRUE),
        ('Canada', '加拿大', 'Canada', TRUE),
        ('Australia', '澳大利亚', 'Australia', TRUE),
        ('Italy', '意大利', 'Italy', TRUE),
        ('Kazakhstan', '哈萨克斯坦', 'Kazakhstan', TRUE),
        ('India', '印度', 'India', TRUE),
        ('印度 India', '印度', 'India', TRUE),
        ('Poland', '波兰', 'Poland', TRUE),
        ('BBC', NULL::varchar, NULL::varchar, FALSE)
),
normalized_regions AS (
    SELECT
        rr.movie_id,
        CASE
            WHEN am.is_valid = FALSE THEN NULL
            WHEN am.canonical_name IS NOT NULL THEN am.canonical_name
            WHEN rr.raw_name ~ '[A-Za-z]' AND rr.raw_name ~ '[^[:ascii:]]'
                THEN NULLIF(TRIM(regexp_replace(rr.raw_name, '\s+[A-Za-z][A-Za-z .-]*$', '')), '')
            ELSE rr.raw_name
            END AS canonical_name
    FROM raw_regions rr
             LEFT JOIN alias_map am ON lower(rr.raw_name) = lower(am.alias_name)
    WHERE rr.raw_name IS NOT NULL
)
INSERT INTO movie_region_relation (movie_id, region_id)
SELECT DISTINCT nr.movie_id, r.id
FROM normalized_regions nr
         JOIN regions r ON r.name = nr.canonical_name
WHERE nr.canonical_name IS NOT NULL
  AND nr.canonical_name <> ''
ON CONFLICT (movie_id, region_id) DO NOTHING;

-- 历史脏数据迁移: 先把旧别名关系迁移到规范地区
WITH alias_cleanup AS (
    SELECT old_r.id AS old_region_id, new_r.id AS new_region_id
    FROM (VALUES
              ('USA', '美国'),
              ('U.S.A.', '美国'),
              ('United States', '美国'),
              ('UK', '英国'),
              ('United Kingdom', '英国'),
              ('China', '中国'),
              ('Mainland China', '中国大陆'),
              ('Hong Kong', '中国香港'),
              ('Taiwan', '中国台湾'),
              ('South Korea', '韩国'),
              ('Korea', '韩国'),
              ('Japan', '日本'),
              ('France', '法国'),
              ('Germany', '德国'),
              ('Spain', '西班牙'),
              ('Russia', '俄罗斯'),
              ('Canada', '加拿大'),
              ('Australia', '澳大利亚'),
              ('Italy', '意大利'),
              ('Kazakhstan', '哈萨克斯坦'),
              ('India', '印度'),
              ('印度 India', '印度'),
              ('Poland', '波兰')
         ) AS m(old_name, new_name)
             JOIN regions old_r ON old_r.name = m.old_name
             JOIN regions new_r ON new_r.name = m.new_name
)
INSERT INTO movie_region_relation (movie_id, region_id)
SELECT DISTINCT mrr.movie_id, ac.new_region_id
FROM movie_region_relation mrr
         JOIN alias_cleanup ac ON mrr.region_id = ac.old_region_id
ON CONFLICT (movie_id, region_id) DO NOTHING;

-- 删除旧别名关系
WITH alias_cleanup AS (
    SELECT old_r.id AS old_region_id
    FROM (VALUES
              ('USA'),
              ('U.S.A.'),
              ('United States'),
              ('UK'),
              ('United Kingdom'),
              ('China'),
              ('Mainland China'),
              ('Hong Kong'),
              ('Taiwan'),
              ('South Korea'),
              ('Korea'),
              ('Japan'),
              ('France'),
              ('Germany'),
              ('Spain'),
              ('Russia'),
              ('Canada'),
              ('Australia'),
              ('Italy'),
              ('Kazakhstan'),
              ('India'),
              ('印度 India'),
              ('BBC')
         ) AS m(old_name)
             JOIN regions old_r ON old_r.name = m.old_name
)
DELETE
FROM movie_region_relation mrr
    USING alias_cleanup ac
WHERE mrr.region_id = ac.old_region_id;

-- 删除旧别名地区行
DELETE
FROM regions
WHERE name IN (
               'USA', 'U.S.A.', 'United States', 'UK', 'United Kingdom',
               'China', 'Mainland China', 'Hong Kong', 'Taiwan',
               'South Korea', 'Korea', 'Japan', 'France', 'Germany', 'Spain', 'Russia',
               'Canada', 'Australia',
               'Italy', 'Kazakhstan', 'India', '印度 India', 'BBC', 'Poland'
    );

-- ----------------------------
-- 3. 迁移 Languages 数据
-- ----------------------------

-- 为 language 归一化准备结构（与 genres/regions 处理方式保持一致）
CREATE TABLE IF NOT EXISTS languages (
    id serial PRIMARY KEY,
    name varchar(100) NOT NULL,
    name_en varchar(100),
    description varchar(255),
    create_time timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_languages_name ON languages (name);

CREATE TABLE IF NOT EXISTS movie_language_relation (
    id bigserial PRIMARY KEY,
    movie_id bigint NOT NULL,
    language_id int NOT NULL,
    create_time timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_movie_language UNIQUE (movie_id, language_id)
);

CREATE INDEX IF NOT EXISTS idx_movie_language_movie_id ON movie_language_relation (movie_id);
CREATE INDEX IF NOT EXISTS idx_movie_language_language_id ON movie_language_relation (language_id);

-- 说明:
-- 1) 兼容常见分隔符（/、，,、;）
-- 2) 兼容中英混合值（如: "普通话 Mandarin"）
-- 3) 处理常见英文别名（如: "English", "Cantonese"）
-- 4) 过滤明显无效值（如: "N/A", "Unknown"）
WITH raw_languages AS (
    SELECT m.movie_id,
           NULLIF(TRIM(regexp_replace(lang_name, '\s+', ' ', 'g')), '') AS raw_name
    FROM movies m
             CROSS JOIN LATERAL regexp_split_to_table(m.languages, '[/,，、;；|]+') AS lang_name
    WHERE m.languages IS NOT NULL
),
alias_map(alias_name, canonical_name, canonical_en, is_valid) AS (
    VALUES
        ('Mandarin', '普通话', 'Mandarin', TRUE),
        ('Putonghua', '普通话', 'Mandarin', TRUE),
        ('Chinese', '汉语', 'Chinese', TRUE),
        ('Cantonese', '粤语', 'Cantonese', TRUE),
        ('English', '英语', 'English', TRUE),
        ('Japanese', '日语', 'Japanese', TRUE),
        ('Korean', '韩语', 'Korean', TRUE),
        ('French', '法语', 'French', TRUE),
        ('German', '德语', 'German', TRUE),
        ('Spanish', '西班牙语', 'Spanish', TRUE),
        ('Russian', '俄语', 'Russian', TRUE),
        ('Italian', '意大利语', 'Italian', TRUE),
        ('Portuguese', '葡萄牙语', 'Portuguese', TRUE),
        ('Thai', '泰语', 'Thai', TRUE),
        ('Bulgarian', '保加利亚语', 'Bulgarian', TRUE),
        ('Hindi', '印地语', 'Hindi', TRUE),
        ('Polish', '波兰语', 'Polish', TRUE),
        ('Mongolian', '蒙古语', 'Mongolian', TRUE),
        ('Mogolian', '蒙古语', 'Mongolian', TRUE),
        ('Shanghainese', '上海话', 'Shanghainese', TRUE),
        ('Shanghaiese', '上海话', 'Shanghainese', TRUE),
        ('Sicilian', '西西里语', 'Sicilian', TRUE),
        ('Welsh', '威尔士语', 'Welsh', TRUE),
        ('Min Nan', '闽南语', 'Min Nan', TRUE),
        ('N/A', NULL::varchar, NULL::varchar, FALSE),
        ('Unknown', NULL::varchar, NULL::varchar, FALSE),
        ('null', NULL::varchar, NULL::varchar, FALSE),
        ('无', NULL::varchar, NULL::varchar, FALSE),
        ('待定', NULL::varchar, NULL::varchar, FALSE),
        ('-', NULL::varchar, NULL::varchar, FALSE)
),
normalized_languages AS (
    SELECT
        rl.movie_id,
        rl.raw_name,
        CASE
            WHEN am.is_valid = FALSE THEN NULL
            WHEN am.canonical_name IS NOT NULL THEN am.canonical_name
            WHEN rl.raw_name ~ '[A-Za-z]' AND rl.raw_name ~ '[^[:ascii:]]'
                THEN NULLIF(TRIM(regexp_replace(rl.raw_name, '\s+[A-Za-z][A-Za-z .-]*$', '')), '')
            ELSE rl.raw_name
            END AS canonical_name,
        CASE
            WHEN am.is_valid = FALSE THEN NULL
            WHEN am.canonical_en IS NOT NULL THEN am.canonical_en
            WHEN rl.raw_name ~ '[A-Za-z]' AND rl.raw_name ~ '[^[:ascii:]]'
                THEN NULLIF(TRIM((regexp_match(rl.raw_name, '([A-Za-z][A-Za-z .-]*)$'))[1]), '')
            WHEN rl.raw_name ~ '^[A-Za-z][A-Za-z .-]*$' THEN rl.raw_name
            ELSE NULL
            END AS canonical_en
    FROM raw_languages rl
             LEFT JOIN alias_map am ON lower(rl.raw_name) = lower(am.alias_name)
    WHERE rl.raw_name IS NOT NULL
),
language_seed AS (
    SELECT canonical_name AS name,
           MAX(canonical_en) FILTER (WHERE canonical_en IS NOT NULL AND canonical_en <> '') AS name_en
    FROM normalized_languages
    WHERE canonical_name IS NOT NULL
      AND canonical_name <> ''
    GROUP BY canonical_name
)
INSERT INTO languages (name, name_en)
SELECT ls.name, ls.name_en
FROM language_seed ls
ON CONFLICT (name) DO UPDATE
    SET name_en = COALESCE(languages.name_en, EXCLUDED.name_en);

-- 填充 movie_language_relation 表
WITH raw_languages AS (
    SELECT m.movie_id,
           NULLIF(TRIM(regexp_replace(lang_name, '\s+', ' ', 'g')), '') AS raw_name
    FROM movies m
             CROSS JOIN LATERAL regexp_split_to_table(m.languages, '[/,，、;；|]+') AS lang_name
    WHERE m.languages IS NOT NULL
),
alias_map(alias_name, canonical_name, canonical_en, is_valid) AS (
    VALUES
        ('Mandarin', '普通话', 'Mandarin', TRUE),
        ('Putonghua', '普通话', 'Mandarin', TRUE),
        ('Chinese', '汉语', 'Chinese', TRUE),
        ('Cantonese', '粤语', 'Cantonese', TRUE),
        ('English', '英语', 'English', TRUE),
        ('Japanese', '日语', 'Japanese', TRUE),
        ('Korean', '韩语', 'Korean', TRUE),
        ('French', '法语', 'French', TRUE),
        ('German', '德语', 'German', TRUE),
        ('Spanish', '西班牙语', 'Spanish', TRUE),
        ('Russian', '俄语', 'Russian', TRUE),
        ('Bulgarian', '保加利亚语', 'Bulgarian', TRUE),
        ('Italian', '意大利语', 'Italian', TRUE),
        ('Portuguese', '葡萄牙语', 'Portuguese', TRUE),
        ('Thai', '泰语', 'Thai', TRUE),
        ('Hindi', '印地语', 'Hindi', TRUE),
        ('Mongolian', '蒙古语', 'Mongolian', TRUE),
        ('Mogolian', '蒙古语', 'Mongolian', TRUE),
        ('Shanghainese', '上海话', 'Shanghainese', TRUE),
        ('Shanghaiese', '上海话', 'Shanghainese', TRUE),
        ('Sicilian', '西西里语', 'Sicilian', TRUE),
        ('Welsh', '威尔士语', 'Welsh', TRUE),
        ('Min Nan', '闽南语', 'Min Nan', TRUE),
        ('N/A', NULL::varchar, NULL::varchar, FALSE),
        ('Unknown', NULL::varchar, NULL::varchar, FALSE),
        ('null', NULL::varchar, NULL::varchar, FALSE),
        ('无', NULL::varchar, NULL::varchar, FALSE),
        ('待定', NULL::varchar, NULL::varchar, FALSE),
        ('-', NULL::varchar, NULL::varchar, FALSE)
),
normalized_languages AS (
    SELECT
        rl.movie_id,
        CASE
            WHEN am.is_valid = FALSE THEN NULL
            WHEN am.canonical_name IS NOT NULL THEN am.canonical_name
            WHEN rl.raw_name ~ '[A-Za-z]' AND rl.raw_name ~ '[^[:ascii:]]'
                THEN NULLIF(TRIM(regexp_replace(rl.raw_name, '\s+[A-Za-z][A-Za-z .-]*$', '')), '')
            ELSE rl.raw_name
            END AS canonical_name
    FROM raw_languages rl
             LEFT JOIN alias_map am ON lower(rl.raw_name) = lower(am.alias_name)
    WHERE rl.raw_name IS NOT NULL
)
INSERT INTO movie_language_relation (movie_id, language_id)
SELECT DISTINCT nl.movie_id, l.id
FROM normalized_languages nl
         JOIN languages l ON l.name = nl.canonical_name
WHERE nl.canonical_name IS NOT NULL
  AND nl.canonical_name <> ''
ON CONFLICT (movie_id, language_id) DO NOTHING;

-- 历史别名关系合并
WITH alias_cleanup AS (
    SELECT old_l.id AS old_language_id, new_l.id AS new_language_id
    FROM (VALUES
              ('Mandarin', '普通话'),
              ('Putonghua', '普通话'),
              ('Chinese', '汉语'),
              ('Cantonese', '粤语'),
              ('English', '英语'),
              ('Japanese', '日语'),
              ('Korean', '韩语'),
              ('French', '法语'),
              ('German', '德语'),
              ('Spanish', '西班牙语'),
              ('Russian', '俄语'),
              ('Italian', '意大利语'),
              ('Portuguese', '葡萄牙语'),
              ('Thai', '泰语'),
              ('Mongolian', '蒙古语'),
              ('Shanghainese', '上海话'),
              ('Sicilian', '西西里语'),
              ('Welsh', '威尔士语'),
              ('Bulgarian', '保加利亚语'),
              ('Hindi', '印地语'),
              ('Min Nan', '闽南语'),
              ('Polish', '波兰语')
         ) AS m(old_name, new_name)
             JOIN languages old_l ON old_l.name = m.old_name
             JOIN languages new_l ON new_l.name = m.new_name
)
INSERT INTO movie_language_relation (movie_id, language_id)
SELECT DISTINCT mlr.movie_id, ac.new_language_id
FROM movie_language_relation mlr
         JOIN alias_cleanup ac ON mlr.language_id = ac.old_language_id
ON CONFLICT (movie_id, language_id) DO NOTHING;

-- 删除旧别名关系
WITH alias_cleanup AS (
    SELECT old_l.id AS old_language_id
    FROM (VALUES
              ('Mandarin'),
              ('Putonghua'),
              ('Chinese'),
              ('Cantonese'),
              ('English'),
              ('Japanese'),
              ('Korean'),
              ('French'),
              ('German'),
              ('Spanish'),
              ('Russian'),
              ('Italian'),
              ('Portuguese'),
              ('Bulgarian'),
              ('Thai'),
              ('Hindi'),
              ('Min Nan'),
              ('Mongolian'),
              ('Mogolian'),
              ('Shanghainese'),
              ('Shanghaiese'),
              ('Sicilian'),
              ('Welsh'),
              ('Polish'),
              ('N/A'),
              ('Unknown'),
              ('null'),
              ('-')
         ) AS m(old_name)
             JOIN languages old_l ON old_l.name = m.old_name
)
DELETE
FROM movie_language_relation mlr
    USING alias_cleanup ac
WHERE mlr.language_id = ac.old_language_id;

-- 删除旧别名语言行
DELETE
FROM languages
WHERE name IN (
               'Mandarin', 'Putonghua', 'Chinese', 'Cantonese', 'English',
               'Japanese', 'Korean', 'French', 'German', 'Spanish',
               'Russian', 'Italian', 'Portuguese', 'Thai', 'Hindi',
               'Min Nan', 'N/A', 'Unknown', 'null', '-', 'Bulgarian', 'Polish', 
               'Mongolian', 'Mogolian', 'Shanghainese', 'Shanghaiese', 'Sicilian', 'Welsh'
    );
