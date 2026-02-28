package com.movie.backend.mapper;

import com.movie.backend.dto.SimilarMovieDTO;
import com.movie.backend.dto.UserRecDTO;
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
     * 查询用户个性化推荐
     */
    List<UserRecDTO> selectUserRecs(@Param("userId") String userId,
                                     @Param("limit") int limit);

    /**
     * 查询相似电影
     */
    List<SimilarMovieDTO> selectSimilarMovies(@Param("movieId") Long movieId,
                                               @Param("similarityType") Integer similarityType,
                                               @Param("limit") int limit);
}
