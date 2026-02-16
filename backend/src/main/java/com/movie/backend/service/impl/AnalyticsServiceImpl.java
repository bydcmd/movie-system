package com.movie.backend.service.impl;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.ColdGemVO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.mapper.StatsHiddenGemsMapper;
import com.movie.backend.service.AnalyticsService;
import com.movie.backend.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final String PERSONALIZED_CACHE_PREFIX = "recs:personalized:";
    private static final String PERSONALIZED_CACHE_VERSION_KEY = "recs:personalized:version";
    private static final String PERSONALIZED_CACHE_USER_VERSION_PREFIX = "recs:personalized:version:user:";
    private static final long PERSONALIZED_CACHE_VERSION_TTL_DAYS = 30;
    private static final long PERSONALIZED_CACHE_TTL_HOURS = 6;
    private static final String REALTIME_RECS_PREFIX = "recs:realtime:";

    @Autowired
    private MovieService movieService;

    @Autowired
    private MovieMapper movieMapper;

    @Autowired
    @Qualifier("mysqlJdbcTemplate")
    private JdbcTemplate mysqlJdbcTemplate;

    @Autowired
    private StatsHiddenGemsMapper statsHiddenGemsMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<Movie> getHotMoviesByPeriod(String period, int limit) {
        return getFromSparkResult(period, limit);
    }

    @Override
    public List<TrendingMovieDTO> getTrendingMovies(TrendPeriod period, int limit) {
        List<TrendingMovieDTO> results = movieMapper.selectTrendingWithScore(period.name(), limit);
        if (results == null) {
            return new ArrayList<>();
        }
        int rank = 1;
        for (TrendingMovieDTO dto : results) {
            dto.setRank(rank++);
        }
        return results;
    }

    private List<Movie> getFromSparkResult(String period, int limit) {
        // 对应 Spark 任务写入的表名 stats_hot_movies
        String sql = "SELECT movie_id " +
                "FROM stats_hot_movies " +
                "WHERE period_type = ? " +
                "AND calc_date = (SELECT MAX(calc_date) FROM stats_hot_movies WHERE period_type = ?) " +
                "ORDER BY hot_score DESC " +
                "LIMIT ?";
        String normalizedPeriod = period.toUpperCase();
        List<Long> movieIds = mysqlJdbcTemplate.queryForList(sql, Long.class, normalizedPeriod, normalizedPeriod, limit);
        return enrichMovies(movieIds);
    }

    @Override
    public List<ColdGemVO> getHiddenGems(int limit) {
        List<Movie> movies = statsHiddenGemsMapper.selectLatestWithReason(limit);
        List<ColdGemVO> result = new ArrayList<>();
        if (movies == null) {
            return result;
        }
        for (Movie movie : movies) {
            ColdGemVO vo = ColdGemVO.fromMovie(movie);
            vo.setReason(movie.getReason());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<Movie> getPersonalizedMovies(String userId, int limit) {
        // 新用户（未登录）直接兜底到热门日榜
        if (userId == null || userId.trim().isEmpty()) {
            return getHotMoviesByPeriod("DAILY", limit);
        }

        List<Movie> realtime = getRealtimeRecommendations(userId, limit);
        if (realtime != null && !realtime.isEmpty()) {
            return realtime;
        }

        String cacheKey = buildPersonalizedCacheKey(userId, limit);
        List<Movie> cached = getPersonalizedFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Movie> result = loadPersonalizedFromDb(userId, limit);
        if (result == null || result.isEmpty()) {
            // 无离线推荐结果，走兜底逻辑
            return getHotMoviesByPeriod("DAILY", limit);
        }

        cachePersonalizedMovies(cacheKey, result);
        return result;
    }

    @Override
    public void evictPersonalizedCache(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            bumpPersonalizedCacheVersion();
            return;
        }
        bumpPersonalizedCacheUserVersion(userId);
    }

    @Override
    public List<Movie> getSimilarMovies(Long movieId, Integer similarityType, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT similar_movie_id, similarity_score, similarity_type " +
                "FROM stats_similar_movies " +
                "WHERE movie_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(movieId);

        if (similarityType != null) {
            sql.append("AND similarity_type = ? ");
            params.add(similarityType);
        }
        sql.append("ORDER BY similarity_score DESC LIMIT ?");
        params.add(limit);

        List<Map<String, Object>> rows = mysqlJdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Movie> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long similarMovieId = ((Number) row.get("similar_movie_id")).longValue();
            Movie movie = movieService.getDetail(similarMovieId);
            if (movie == null) {
                continue;
            }
            Double similarityScore = row.get("similarity_score") == null
                    ? null : ((Number) row.get("similarity_score")).doubleValue();
            Integer type = row.get("similarity_type") == null
                    ? null : ((Number) row.get("similarity_type")).intValue();
            movie.setReason(buildSimilarReason(type, similarityScore));
            result.add(movie);
        }
        return result;
    }

    private String buildPersonalizedReason(String algorithmType, Double score) {
        String algorithm = algorithmType == null ? "ALS" : algorithmType.toUpperCase(Locale.ROOT);
        if (score == null) {
            return "基于" + algorithm + "离线模型推荐";
        }
        return "基于" + algorithm + "离线模型推荐，匹配度 " + String.format(Locale.ROOT, "%.3f", score);
    }

    private List<Movie> getRealtimeRecommendations(String userId, int limit) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        String key = REALTIME_RECS_PREFIX + userId;
        var ids = stringRedisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        List<Movie> result = new ArrayList<>();
        for (String idStr : ids) {
            if (idStr == null || idStr.isBlank()) {
                continue;
            }
            try {
                Long movieId = Long.parseLong(idStr);
                Movie movie = movieService.getDetail(movieId);
                if (movie == null) {
                    continue;
                }
                movie.setReason("实时推荐");
                result.add(movie);
            } catch (NumberFormatException ignored) {
                // skip invalid id
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Movie> getPersonalizedFromCache(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached instanceof List<?>) {
            return (List<Movie>) cached;
        }
        redisTemplate.delete(cacheKey);
        return null;
    }

    private void cachePersonalizedMovies(String cacheKey, List<Movie> movies) {
        if (movies == null || movies.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().set(cacheKey, movies, PERSONALIZED_CACHE_TTL_HOURS, TimeUnit.HOURS);
    }

    private String buildPersonalizedCacheKey(String userId, int limit) {
        long version = getPersonalizedCacheVersion();
        long userVersion = getPersonalizedCacheUserVersion(userId);
        return PERSONALIZED_CACHE_PREFIX + "v" + version + ":u" + userVersion + ":" + userId + ":" + limit;
    }

    private long getPersonalizedCacheVersion() {
        Object val = redisTemplate.opsForValue().get(PERSONALIZED_CACHE_VERSION_KEY);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        if (val != null) {
            try {
                return Long.parseLong(String.valueOf(val));
            } catch (NumberFormatException ignored) {
                // fall through to initialize
            }
        }
        redisTemplate.opsForValue().set(PERSONALIZED_CACHE_VERSION_KEY, 1L, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
        return 1L;
    }

    private void bumpPersonalizedCacheVersion() {
        Long next = redisTemplate.opsForValue().increment(PERSONALIZED_CACHE_VERSION_KEY);
        if (next == null) {
            redisTemplate.opsForValue().set(PERSONALIZED_CACHE_VERSION_KEY, 1L, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
            return;
        }
        if (next == 1L) {
            redisTemplate.expire(PERSONALIZED_CACHE_VERSION_KEY, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    private long getPersonalizedCacheUserVersion(String userId) {
        String key = buildPersonalizedCacheUserVersionKey(userId);
        Object val = redisTemplate.opsForValue().get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        if (val != null) {
            try {
                return Long.parseLong(String.valueOf(val));
            } catch (NumberFormatException ignored) {
                // fall through to initialize
            }
        }
        redisTemplate.opsForValue().set(key, 1L, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
        return 1L;
    }

    private void bumpPersonalizedCacheUserVersion(String userId) {
        String key = buildPersonalizedCacheUserVersionKey(userId);
        Long next = redisTemplate.opsForValue().increment(key);
        if (next == null) {
            redisTemplate.opsForValue().set(key, 1L, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
            return;
        }
        if (next == 1L) {
            redisTemplate.expire(key, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    private String buildPersonalizedCacheUserVersionKey(String userId) {
        return PERSONALIZED_CACHE_USER_VERSION_PREFIX + userId;
    }

    private List<Movie> loadPersonalizedFromDb(String userId, int limit) {
        String sql = "SELECT movie_id, score, algorithm_type " +
                "FROM stats_user_recs " +
                "WHERE user_id = ? " +
                "ORDER BY score DESC " +
                "LIMIT ?";
        List<Map<String, Object>> recRows = mysqlJdbcTemplate.queryForList(sql, userId, limit);
        if (recRows == null || recRows.isEmpty()) {
            return new ArrayList<>();
        }

        List<Movie> result = new ArrayList<>();
        for (Map<String, Object> row : recRows) {
            Long movieId = ((Number) row.get("movie_id")).longValue();
            Movie movie = movieService.getDetail(movieId);
            if (movie == null) {
                continue;
            }
            Double score = row.get("score") == null ? null : ((Number) row.get("score")).doubleValue();
            String algorithmType = row.get("algorithm_type") == null ? "ALS" : String.valueOf(row.get("algorithm_type"));
            movie.setReason(buildPersonalizedReason(algorithmType, score));
            result.add(movie);
        }
        return result;
    }

    private String buildSimilarReason(Integer similarityType, Double similarityScore) {
        String typeText = "相似推荐";
        if (similarityType != null) {
            if (similarityType == 1) {
                typeText = "内容相似";
            } else if (similarityType == 2) {
                typeText = "协同过滤相似";
            }
        }
        if (similarityScore == null) {
            return typeText + "结果";
        }
        return typeText + "，相似度 " + String.format(Locale.ROOT, "%.3f", similarityScore);
    }

    private List<Movie> enrichMovies(List<Long> movieIds) {
        List<Movie> movies = new ArrayList<>();
        for (Long id : movieIds) {
            Movie m = movieService.getDetail(id);
            if (m != null) {
                movies.add(m);
            }
        }
        return movies;
    }
}
