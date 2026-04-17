package com.movie.backend.mapper;

import com.movie.backend.entity.Rating;
import com.movie.backend.dto.MyRatingVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface RatingMapper {
    /**
     * Insert rating
     */
    int insert(Rating rating);

    /**
     * 更新用户的评分
     */
    int updateByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId, @Param("rating") Integer rating, @Param("ratingTime") LocalDateTime ratingTime);

    /**
     * 写入或更新用户评分（相同评分不重复更新）
     */
    int upsertByUserAndMovie(@Param("userId") String userId,
                             @Param("movieId") Long movieId,
                             @Param("rating") Integer rating,
                             @Param("ratingTime") LocalDateTime ratingTime);

    /**
     * Get user's rating for a movie
     */
    Rating selectByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId);

    /**
     * 获取用户的评分列表
     */
    List<Rating> selectByUserId(@Param("userId") String userId);

    /**
     * 获取用户已评分电影ID列表
     */
    List<Long> selectMovieIdsByUserId(@Param("userId") String userId);

    /**
     * 获取指定电影的所有评分
     */
    List<Rating> selectByMovieId(@Param("movieId") Long movieId);

    /**
     * 获取用户的评分 VO 列表 (包含电影信息)
     */
    List<MyRatingVO> selectVOByUserId(@Param("userId") String userId);

    /**
     * 删除用户的所有评分记录
     */
    int deleteByUserId(@Param("userId") String userId);

    /**
     * 批量删除用户的评分记录（按电影ID）
     */
    int deleteBatch(@Param("userId") String userId, @Param("movieIds") List<Long> movieIds);

    /**
     * 根据电影ID删除评分
     */
    int deleteByMovieId(@Param("movieId") Long movieId);

    /**
     * 基于用户评分重算电影的本站评分与评分人数
     */
    int refreshMovieScoreAndVotes(@Param("movieId") Long movieId,
                                  @Param("forceUpdateVotesThreshold") Integer forceUpdateVotesThreshold);

    /**
     * 批量重算电影的本站评分与评分人数
     */
    int refreshMovieScoreAndVotesBatch(@Param("movieIds") Collection<Long> movieIds,
                                       @Param("forceUpdateVotesThreshold") Integer forceUpdateVotesThreshold);
}
