-- 1) 刷新 movies 三个缓存字段
DROP FUNCTION IF EXISTS public.refresh_movie_relation_cache(bigint);
CREATE OR REPLACE FUNCTION public.refresh_movie_relation_cache(p_movie_id bigint)
  RETURNS void AS
  $$
BEGIN
UPDATE public.movies m
SET
    genres = g.genres,
    languages = l.languages,
    regions = r.regions
    FROM
          (
              SELECT mgr.movie_id,
                     string_agg(DISTINCT g.name, ' / ' ORDER BY g.name) AS genres
              FROM public.movie_genre_relation mgr
              JOIN public.genres g ON g.id = mgr.genre_id
              WHERE mgr.movie_id = p_movie_id
              GROUP BY mgr.movie_id
          ) g
          FULL JOIN
          (
              SELECT mlr.movie_id,
                     string_agg(DISTINCT l.name, ' / ' ORDER BY l.name) AS languages
              FROM public.movie_language_relation mlr
              JOIN public.languages l ON l.id = mlr.language_id
              WHERE mlr.movie_id = p_movie_id
              GROUP BY mlr.movie_id
          ) l ON l.movie_id = g.movie_id
    FULL JOIN
    (
    SELECT mrr.movie_id,
    string_agg(DISTINCT r.name, ' / ' ORDER BY r.name) AS regions
    FROM public.movie_region_relation mrr
    JOIN public.regions r ON r.id = mrr.region_id
    WHERE mrr.movie_id = p_movie_id
    GROUP BY mrr.movie_id
    ) r ON r.movie_id = COALESCE(g.movie_id, l.movie_id)
WHERE m.movie_id = p_movie_id;

-- 没有关联记录时清空缓存列
UPDATE public.movies
SET
    genres = (
        SELECT string_agg(DISTINCT g.name, ' / ' ORDER BY g.name)
        FROM public.movie_genre_relation mgr
                 JOIN public.genres g ON g.id = mgr.genre_id
        WHERE mgr.movie_id = p_movie_id
    ),
    languages = (
        SELECT string_agg(DISTINCT l.name, ' / ' ORDER BY l.name)
        FROM public.movie_language_relation mlr
                 JOIN public.languages l ON l.id = mlr.language_id
        WHERE mlr.movie_id = p_movie_id
    ),
    regions = (
        SELECT string_agg(DISTINCT r.name, ' / ' ORDER BY r.name)
        FROM public.movie_region_relation mrr
                 JOIN public.regions r ON r.id = mrr.region_id
        WHERE mrr.movie_id = p_movie_id
    )
WHERE movie_id = p_movie_id;
END;
  $$ LANGUAGE plpgsql;


  -- 2) 通用触发器函数：处理 insert/update/delete
DROP FUNCTION IF EXISTS public.trigger_refresh_movie_relation_cache();
CREATE OR REPLACE FUNCTION public.trigger_refresh_movie_relation_cache()
  RETURNS trigger AS
  $$
BEGIN
      IF TG_OP = 'DELETE' THEN
          PERFORM public.refresh_movie_relation_cache(OLD.movie_id);
RETURN OLD;
END IF;

      IF TG_OP = 'UPDATE' THEN
          IF OLD.movie_id IS DISTINCT FROM NEW.movie_id THEN
              PERFORM public.refresh_movie_relation_cache(OLD.movie_id);
END IF;
          PERFORM public.refresh_movie_relation_cache(NEW.movie_id);
RETURN NEW;
END IF;

      PERFORM public.refresh_movie_relation_cache(NEW.movie_id);
RETURN NEW;
END;
  $$ LANGUAGE plpgsql;


  -- 3) 三张关系表挂触发器
DROP TRIGGER IF EXISTS trg_movie_genre_cache ON public.movie_genre_relation;
CREATE TRIGGER trg_movie_genre_cache
    AFTER INSERT OR UPDATE OR DELETE ON public.movie_genre_relation
    FOR EACH ROW
    EXECUTE FUNCTION public.trigger_refresh_movie_relation_cache();

DROP TRIGGER IF EXISTS trg_movie_language_cache ON public.movie_language_relation;
CREATE TRIGGER trg_movie_language_cache
    AFTER INSERT OR UPDATE OR DELETE ON public.movie_language_relation
    FOR EACH ROW
    EXECUTE FUNCTION public.trigger_refresh_movie_relation_cache();

DROP TRIGGER IF EXISTS trg_movie_region_cache ON public.movie_region_relation;
CREATE TRIGGER trg_movie_region_cache
    AFTER INSERT OR UPDATE OR DELETE ON public.movie_region_relation
    FOR EACH ROW
    EXECUTE FUNCTION public.trigger_refresh_movie_relation_cache();


-- 4) 给历史数据回填一次
UPDATE public.movies m
SET
    genres = (
        SELECT string_agg(DISTINCT g.name, ' / ' ORDER BY g.name)
        FROM public.movie_genre_relation mgr
                 JOIN public.genres g ON g.id = mgr.genre_id
        WHERE mgr.movie_id = m.movie_id
    ),
    languages = (
        SELECT string_agg(DISTINCT l.name, ' / ' ORDER BY l.name)
        FROM public.movie_language_relation mlr
                 JOIN public.languages l ON l.id = mlr.language_id
        WHERE mlr.movie_id = m.movie_id
    ),
    regions = (
        SELECT string_agg(DISTINCT r.name, ' / ' ORDER BY r.name)
        FROM public.movie_region_relation mrr
                 JOIN public.regions r ON r.id = mrr.region_id
        WHERE mrr.movie_id = m.movie_id
    );