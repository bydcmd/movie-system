package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.CommentVO;
import com.movie.backend.dto.LongReviewDTO;
import com.movie.backend.entity.Comment;

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
     * Submit comment
     */
    void submitComment(String userId, Long movieId, String content);

    /**
     * 获取用户对某部电影的评论
     */
    Comment getUserComment(String userId, Long movieId);

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
     * 删除评论
     */
    void deleteComment(String userId, Long commentId);

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

}
