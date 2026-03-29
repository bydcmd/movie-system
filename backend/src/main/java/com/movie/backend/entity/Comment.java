package com.movie.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "评论实体")
public class Comment {
    @Schema(description = "评论ID", example = "1712345678901")
    private Long id;

    @Schema(description = "用户ID", example = "movie_fan_01")
    private String userId;

    @Schema(description = "电影ID", example = "1292052")
    private Long movieId;

    @Schema(description = "评论标题 (长评专用)", example = "《肖申克的救赎》：由于恐惧而受其所累")
    private String title;

    @Schema(description = "评论类型 (1:短评, 2:长评)", example = "1")
    private Integer type; // 1: Short, 2: Long

    @Schema(description = "评论内容", example = "这部电影太经典了，值得反复观看！")
    private String content;

    @Schema(description = "获赞数", example = "1024")
    private Integer votes;

    @Schema(description = "评论发布时间")
    private Date commentTime;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "评论状态 (1:草稿, 2:发布, 3:隐藏)", example = "2")
    private Integer status;
}
