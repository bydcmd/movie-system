package com.movie.backend.mapper;

import com.movie.backend.dto.SearchFunnelDTO;
import com.movie.backend.dto.SearchKeywordInsightDTO;
import com.movie.backend.dto.SimilarMovieDTO;
import com.movie.backend.dto.UserFunnelDTO;
import com.movie.backend.dto.UserRetentionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnalyticsMapper {

    /**
     * 查询热门电影ID列表（取最新 calc_date）
     */
    List<Long> selectHotMovieIds(@Param("period") String period,
                                  @Param("limit") int limit);

    /**
     * 查询相似电影
     */
    List<SimilarMovieDTO> selectSimilarMovies(@Param("movieId") Long movieId,
                                               @Param("similarityType") Integer similarityType,
                                               @Param("limit") int limit);

    /**
     * 查询搜索漏斗分析数据（取最新 calc_date）
     */
    SearchFunnelDTO selectSearchFunnel();

    /**
     * 查询搜索关键词洞察数据（取最新 calc_date）
     */
    List<SearchKeywordInsightDTO> selectSearchKeywordInsights(@Param("limit") int limit);

    /**
     * 查询用户漏斗分析数据（取最新 calc_date）
     */
    UserFunnelDTO selectUserFunnel();

    /**
     * 查询用户留存分析数据（取最新 calc_date）
     */
    List<UserRetentionDTO> selectUserRetention(@Param("limit") int limit);
}
