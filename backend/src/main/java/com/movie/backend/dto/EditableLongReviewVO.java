package com.movie.backend.dto;

import com.movie.backend.entity.Comment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "可编辑长评返回对象")
public class EditableLongReviewVO {

    @Schema(description = "评论ID", example = "1712345678901", type = "string")
    private String id;

    @Schema(description = "用户ID", example = "movie_fan_01")
    private String userId;

    @Schema(description = "电影ID", example = "1292052")
    private Long movieId;

    @Schema(description = "评论标题 (长评专用)", example = "《肖申克的救赎》：由于恐惧而受其所累")
    private String title;

    @Schema(description = "评论类型 (1:短评, 2:长评)", example = "2")
    private Integer type;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "获赞数", example = "1024")
    private Integer votes;

    @Schema(description = "评论发布时间")
    private Date commentTime;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "评论状态 (1:草稿, 2:发布, 3:隐藏)", example = "1")
    private Integer status;

    public static EditableLongReviewVO from(Comment comment) {
        if (comment == null) {
            return null;
        }

        EditableLongReviewVO vo = new EditableLongReviewVO();
        vo.setId(comment.getId() != null ? String.valueOf(comment.getId()) : null);
        vo.setUserId(comment.getUserId());
        vo.setMovieId(comment.getMovieId());
        vo.setTitle(comment.getTitle());
        vo.setType(comment.getType());
        vo.setContent(comment.getContent());
        vo.setVotes(comment.getVotes());
        vo.setCommentTime(comment.getCommentTime());
        vo.setVersion(comment.getVersion());
        vo.setStatus(comment.getStatus());
        return vo;
    }
}
