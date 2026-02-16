package com.movie.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "评论点赞记录")
public class CommentLike {
    @Schema(description = "点赞记录ID", example = "5002")
    private Long id;

    @Schema(description = "被点赞的评论ID", example = "1712345678901")
    private Long commentId;

    @Schema(description = "点赞的用户ID", example = "movie_fan_01")
    private String userId;

    @Schema(description = "点赞时间")
    private Date createTime;
}
