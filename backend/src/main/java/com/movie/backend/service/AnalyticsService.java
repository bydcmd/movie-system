package com.movie.backend.service;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.GenrePreferenceDTO;
import com.movie.backend.dto.SearchFunnelDTO;
import com.movie.backend.dto.SearchKeywordInsightDTO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.dto.UserFunnelDTO;
import com.movie.backend.dto.UserRetentionDTO;
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
     * 获取相似电影推荐
     *
     * @param movieId 基准电影ID
     * @param similarityType 相似类型，可为空（1-内容相似,2-协同过滤,3-ALS隐语义相似）
     * @param limit 返回数量
     */
    List<Movie> getSimilarMovies(Long movieId, Integer similarityType, int limit);

    /**
     * 获取搜索漏斗分析数据
     *
     * @return 搜索漏斗数据，无数据时返回null
     */
    SearchFunnelDTO getSearchFunnel();

    /**
     * 获取搜索关键词洞察数据
     *
     * @param limit 返回数量
     * @return 关键词洞察列表
     */
    List<SearchKeywordInsightDTO> getSearchKeywordInsights(int limit);

    /**
     * 获取用户漏斗分析数据
     *
     * @return 用户漏斗数据，无数据时返回null
     */
    UserFunnelDTO getUserFunnel();

    /**
     * 获取用户留存分析数据
     *
     * @param limit 返回数量
     * @return 用户留存数据列表
     */
    List<UserRetentionDTO> getUserRetention(int limit);

    /**
     * 获取类型偏好分析数据
     *
     * @param limit 返回数量
     * @return 类型偏好数据列表
     */
    List<GenrePreferenceDTO> getGenrePreference(int limit);
}
