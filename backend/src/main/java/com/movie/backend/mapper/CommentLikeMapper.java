package com.movie.backend.mapper;

import com.movie.backend.entity.CommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentLikeMapper {
    /**
     * 添加点赞记录
     */
    int insert(CommentLike commentLike);

    /**
     * 删除点赞记录
     */
    int delete(@Param("commentId") Long commentId, @Param("userId") String userId);

    /**
     * 删除某条评论的全部点赞记录
     */
    int deleteByCommentId(@Param("commentId") Long commentId);

    /**
     * 查询是否已点赞
     */
    CommentLike selectByCommentAndUser(@Param("commentId") Long commentId, @Param("userId") String userId);
}
