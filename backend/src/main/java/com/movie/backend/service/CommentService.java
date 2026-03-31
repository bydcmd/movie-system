package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.CommentVO;
import com.movie.backend.dto.LongReviewDTO;
import com.movie.backend.entity.Comment;

import java.util.List;

public interface CommentService {
    /**
     * Get comments by movie ID (Paged)
     */
    PageInfo<Comment> getCommentsByMovieId(Long movieId, int page, int size);

    /**
     * 获取电影评论列表（包含用户评分及基本信息）
     */
    PageInfo<CommentVO> getCommentsWithRatingByMovieId(Long movieId, String currentUserId, int page, int size);

    /**
     * 获取指定电影下的长评详情
     */
    CommentVO getMovieLongReviewDetail(Long movieId, Long commentId, String currentUserId);

    /**
     * Submit comment
     */
    void submitComment(String userId, Long movieId, String content);

    /**
     * 获取用户对某部电影的短评
     */
    Comment getUserShortComment(String userId, Long movieId);

    /**
     * 获取用户对某部电影的长评
     */
    Comment getUserLongReview(String userId, Long movieId);

    /**
     * 修改评论和评分
     */
    void updateCommentWithRating(String userId, Long movieId, String content, Integer rating);

    /**
     * 仅修改评论内容
     */
    void updateComment(String userId, Long movieId, String content);

    /**
     * 点赞评论（幂等）
     * @return 当前点赞状态 (true: 已点赞)
     */
    boolean likeComment(String userId, Long commentId);

    /**
     * 取消点赞评论（幂等）
     * @return 当前点赞状态 (false: 未点赞)
     */
    boolean unlikeComment(String userId, Long commentId);

    /**
     * 检查用户是否已点赞过某评论
     */
    boolean isLiked(String userId, Long commentId);

    /**
     * 获取用户的评论列表（分页）
     */
    PageInfo<Comment> getUserComments(String userId, int page, int size);

    /**
     * 删除评论（支持单个或批量）
     * @param userId 用户ID
     * @param commentIds 评论ID列表
     * @return 成功删除的数量
     */
    int deleteComments(String userId, List<Long> commentIds);

    /**
     * 获取指定类型的评论列表 (1:短评, 2:长评)
     */
    PageInfo<CommentVO> getCommentsByType(Long movieId, String currentUserId, Integer type, int page, int size);

    /**
     * 发布长评（Tiptap JSON 格式）
     */
    void submitLongReview(String userId, LongReviewDTO dto);

    /**
     * 修改长评
     */
    void updateLongReview(String userId, Long movieId, String title, String content);

    /**
     * 保存长评草稿
     */
    void saveLongReviewDraft(String userId, Long movieId, String title, String content);

    /**
     * 更新长评草稿
     */
    void updateLongReviewDraft(String userId, Long movieId, String title, String content);

    /**
     * 发布草稿
     */
    void publishDraft(String userId, Long commentId);

}
