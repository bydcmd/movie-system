package com.movie.backend.service;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.entity.Movie;

import java.util.List;

public interface AnalyticsService {
    /**
     * 获取热门电影
     * @param period 时间周期: DAILY, WEEKLY, MONTHLY, TOTAL
     * @param limit 数量
     */
    List<Movie> getHotMoviesByPeriod(String period, int limit);

    /**
     * 获取趋势榜单
     * @param period 周期类型: DAILY, WEEKLY, MONTHLY, TOTAL
     * @param limit 返回数量
     */
    List<TrendingMovieDTO> getTrendingMovies(TrendPeriod period, int limit);

    /**
     * 获取个性化推荐（猜你喜欢）
     * 新用户或无离线推荐结果时回退到日榜热门
     *
     * @param userId 用户ID，可为空
     * @param limit 返回数量
     */
    List<Movie> getPersonalizedMovies(String userId, int limit);

    /**
     * 清除猜你喜欢缓存（仅影响该用户；传空则刷新全量版本）
     *
     * @param userId 用户ID，可为空
     */
    void evictPersonalizedCache(String userId);

    /**
     * 获取相似电影推荐
     *
     * @param movieId 基准电影ID
     * @param similarityType 相似类型，可为空（1-内容相似,2-协同过滤）
     * @param limit 返回数量
     */
    List<Movie> getSimilarMovies(Long movieId, Integer similarityType, int limit);
}
