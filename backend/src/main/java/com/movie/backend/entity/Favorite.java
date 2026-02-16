package com.movie.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "用户收藏记录")
public class Favorite {
    @Schema(description = "用户ID", example = "movie_fan_01")
    private String userId;

    @Schema(description = "电影ID", example = "1292052")
    private Long movieId;

    @Schema(description = "收藏夹ID，0表示默认收藏夹", example = "1")
    private Long folderId;

    @Schema(description = "收藏时间")
    private Date createTime;
}