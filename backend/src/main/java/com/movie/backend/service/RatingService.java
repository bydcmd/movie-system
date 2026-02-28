package com.movie.backend.service;

import com.movie.backend.entity.Rating;
import com.movie.backend.dto.MyRatingVO;
import com.github.pagehelper.PageInfo;
import java.util.List;

public interface RatingService {
    /**
     * 提交或更新评分
     */
    void updateRating(String userId, Long movieId, Integer rating);

    /**
     * Get user's rating for a movie
     */
    Rating getUserRating(String userId, Long movieId);

    /**
     * 获取用户的评分列表（分页）
     */
    PageInfo<Rating> getUserRatings(String userId, int page, int size);

    /**
     * 获取用户的评分列表（分页，包含电影详细信息）
     */
    PageInfo<MyRatingVO> getMyRatingVOList(String userId, int page, int size);

    /**
     * 清除用户的所有评分记录
     */
    void clearUserRatings(String userId);

    /**
     * 批量删除用户的评分记录（按评分ID）
     */
    void deleteRatingsBatch(String userId, List<Long> movieIds);
}
