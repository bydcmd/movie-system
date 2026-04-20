package com.movie.backend.service;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.GenrePreferenceDTO;
import com.movie.backend.dto.TrendingMovieDTO;
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
