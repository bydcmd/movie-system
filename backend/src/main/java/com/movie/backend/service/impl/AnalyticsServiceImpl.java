package com.movie.backend.service.impl;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.SearchFunnelDTO;
import com.movie.backend.dto.SearchKeywordInsightDTO;
import com.movie.backend.dto.SimilarMovieDTO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.dto.UserFunnelDTO;
import com.movie.backend.dto.UserRecDTO;
import com.movie.backend.dto.UserRetentionDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.mapper.AnalyticsMapper;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final String PERSONALIZED_CACHE_PREFIX = "recs:personalized:";
    private static final String PERSONALIZED_CACHE_VERSION_KEY = "recs:personalized:version";
    private static final String PERSONALIZED_CACHE_USER_VERSION_PREFIX = "recs:personalized:version:user:";
    private static final long PERSONALIZED_CACHE_VERSION_TTL_DAYS = 30;
    private static final long PERSONALIZED_CACHE_TTL_HOURS = 6;

    @Autowired
    private AnalyticsMapper analyticsMapper;

    @Autowired
    private MovieMapper movieMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<Movie> getHotMoviesByPeriod(String period, int limit) {
        List<Long> movieIds = analyticsMapper.selectHotMovieIds(period.toUpperCase(), limit);
        return enrichMovies(movieIds);
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

    @Override
    public List<Movie> getPersonalizedMovies(String userId, int limit) {
        if (userId == null || userId.trim().isEmpty()) {
            return getHotMoviesByPeriod("DAILY", limit);
        }

        String cacheKey = buildPersonalizedCacheKey(userId, limit);
        List<Movie> cached = getPersonalizedFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Movie> result = loadPersonalizedFromDb(userId, limit);
        if (result.isEmpty()) {
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
        List<SimilarMovieDTO> dtos = analyticsMapper.selectSimilarMovies(movieId, similarityType, limit);
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = dtos.stream().map(SimilarMovieDTO::getSimilarMovieId).collect(Collectors.toList());
        Map<Long, Movie> movieMap = batchLoadMovies(ids);

        List<Movie> result = new ArrayList<>();
        for (SimilarMovieDTO dto : dtos) {
            Movie movie = movieMap.get(dto.getSimilarMovieId());
            if (movie == null) {
                continue;
            }
            movie.setReason(buildSimilarReason(dto.getSimilarityType(), dto.getSimilarityScore()));
            result.add(movie);
        }
        return result;
    }

    @Override
    public SearchFunnelDTO getSearchFunnel() {
        return analyticsMapper.selectSearchFunnel();
    }

    @Override
    public List<SearchKeywordInsightDTO> getSearchKeywordInsights(int limit) {
        List<SearchKeywordInsightDTO> results = analyticsMapper.selectSearchKeywordInsights(limit);
        return results == null ? new ArrayList<>() : results;
    }

    @Override
    public UserFunnelDTO getUserFunnel() {
        return analyticsMapper.selectUserFunnel();
    }

    @Override
    public List<UserRetentionDTO> getUserRetention(int limit) {
        List<UserRetentionDTO> results = analyticsMapper.selectUserRetention(limit);
        return results == null ? new ArrayList<>() : results;
    }

    // ---- private helpers ----

    private List<Movie> loadPersonalizedFromDb(String userId, int limit) {
        List<UserRecDTO> recs = analyticsMapper.selectUserRecs(userId, limit);
        if (recs == null || recs.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = recs.stream().map(UserRecDTO::getMovieId).collect(Collectors.toList());
        Map<Long, Movie> movieMap = batchLoadMovies(ids);

        List<Movie> result = new ArrayList<>();
        for (UserRecDTO rec : recs) {
            Movie movie = movieMap.get(rec.getMovieId());
            if (movie == null) {
                continue;
            }
            movie.setReason(buildPersonalizedReason(rec.getAlgorithmType(), rec.getScore()));
            result.add(movie);
        }
        return result;
    }

    private Map<Long, Movie> batchLoadMovies(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Movie> movies = movieMapper.selectByIds(ids);
        return movies.stream().collect(Collectors.toMap(Movie::getId, Function.identity(), (a, b) -> a));
    }

    private List<Movie> enrichMovies(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Movie> movies = movieMapper.selectByIds(movieIds);
        return movies == null ? new ArrayList<>() : movies;
    }

    private String buildPersonalizedReason(String algorithmType, Double score) {
        String algorithm = algorithmType == null ? "ALS" : algorithmType.toUpperCase(Locale.ROOT);
        if (score == null) {
            return "基于" + algorithm + "离线模型推荐";
        }
        return "基于" + algorithm + "离线模型推荐，匹配度 " + String.format(Locale.ROOT, "%.3f", score);
    }

    private String buildSimilarReason(Integer similarityType, Double similarityScore) {
        String typeText = "相似推荐";
        if (similarityType != null) {
            if (similarityType == 1) {
                typeText = "内容相似";
            } else if (similarityType == 2) {
                typeText = "协同过滤相似";
            } else if (similarityType == 3) {
                typeText = "ALS 隐语义相似";
            }
        }
        if (similarityScore == null) {
            return typeText + "结果";
        }
        return typeText + "，相似度 " + String.format(Locale.ROOT, "%.3f", similarityScore);
    }

    // ---- cache helpers ----

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
        String key = PERSONALIZED_CACHE_USER_VERSION_PREFIX + userId;
        Object val = redisTemplate.opsForValue().get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        if (val != null) {
            try {
                return Long.parseLong(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        redisTemplate.opsForValue().set(key, 1L, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
        return 1L;
    }

    private void bumpPersonalizedCacheUserVersion(String userId) {
        String key = PERSONALIZED_CACHE_USER_VERSION_PREFIX + userId;
        Long next = redisTemplate.opsForValue().increment(key);
        if (next == null) {
            redisTemplate.opsForValue().set(key, 1L, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
            return;
        }
        if (next == 1L) {
            redisTemplate.expire(key, PERSONALIZED_CACHE_VERSION_TTL_DAYS, TimeUnit.DAYS);
        }
    }
}
