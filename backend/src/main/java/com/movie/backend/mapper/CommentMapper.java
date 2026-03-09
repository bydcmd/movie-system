package com.movie.backend.mapper;

import com.movie.backend.entity.Comment;
import com.movie.backend.dto.CommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {
    int deleteById(@Param("id") Long id);

    List<Comment> selectList(@Param("keyword") String keyword);

    /**
     * Get comments by movie ID
     */
    List<Comment> selectByMovieId(@Param("movieId") Long movieId);

    /**
     * 检查用户是否已发布过指定类型的评论
     */
    int countByUserAndMovieAndType(@Param("userId") String userId, @Param("movieId") Long movieId, @Param("type") Integer type);

    /**
     * 获取指定类型的评论列表 (带评分)
     */
    List<CommentVO> selectWithRatingByMovieIdAndType(
            @Param("movieId") Long movieId,
            @Param("currentUserId") String currentUserId,
            @Param("type") Integer type
    );

    /**
     * 获取电影评论及对应的用户评分
     */
    List<CommentVO> selectWithRatingByMovieId(@Param("movieId") Long movieId, @Param("currentUserId") String currentUserId);

    /**
     * Insert comment
     */
    int insert(Comment comment);

    /**
     * 按评论类型更新用户的评论内容
     */
    int updateByUserAndMovieAndType(
            @Param("userId") String userId,
            @Param("movieId") Long movieId,
            @Param("type") Integer type,
            @Param("content") String content,
            @Param("commentTime") java.util.Date commentTime
    );

    /**
     * 获取用户的评论列表
     */
    List<Comment> selectByUserId(@Param("userId") String userId);

    /**
     * 检查用户是否已对该电影发表过评论
     */
    int countByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId);

    /**
     * 获取用户对某部电影的指定类型评论
     */
    Comment selectByUserAndMovieAndType(
            @Param("userId") String userId,
            @Param("movieId") Long movieId,
            @Param("type") Integer type
    );

    /**
     * 更新评论的点赞数
     * @param id 评论ID
     * @param delta 变化值，如 +1 或 -1
     */
    int updateVotes(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 使用乐观锁更新评论的点赞数
     * @param id 评论ID
     * @param delta 变化值，如 +1 或 -1
     * @param version 当前版本号
     * @return 影响的行数，0表示更新失败（版本冲突）
     */
    int updateVotesWithVersion(@Param("id") Long id, @Param("delta") int delta, @Param("version") Integer version);
    
    /**
     * 统计用户获得的总赞数
     */
    Integer getTotalReceivedLikes(@Param("userId") String userId);
    
    /**
     * 统计用户发布的评论总数
     */
    Integer getCommentCount(@Param("userId") String userId);

    /**
     * 删除用户的评论（根据ID和用户ID双重校验）
     */
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);

    /**
     * 更新长评（带标题）
     */
    int updateLongComment(
            @Param("userId") String userId,
            @Param("movieId") Long movieId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("commentTime") java.util.Date commentTime
    );

    /**
     * Count all comments.
     */
    int countAll();
}
