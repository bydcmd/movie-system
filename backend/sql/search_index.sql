-- 1. 创建包含所有文本信息（含编剧）的虚拟列
ALTER TABLE movies
    ADD COLUMN full_search_text TEXT
        GENERATED ALWAYS AS (
            CONCAT_WS(' ',
                      name,
                      IFNULL(alias, ''),
                      IFNULL(storyline, ''),
                      JSON_UNQUOTE(JSON_EXTRACT(directors, '$[*].name')),
                      JSON_UNQUOTE(JSON_EXTRACT(actors, '$[*].name')),
                      JSON_UNQUOTE(JSON_EXTRACT(writers, '$[*].name')) -- 新增编剧字段
            )
            ) STORED;

-- 2. 创建单一全文索引
ALTER TABLE movies
    ADD FULLTEXT INDEX idx_global_search (full_search_text)
WITH PARSER ngram;