package com.movie.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "用户浏览历史记录")
public class ViewHistory {
    @Schema(description = "历史记录ID", example = "1")
    private Long id;

    @Schema(description = "用户ID", example = "movie_fan_01")
    private String userId;

    @Schema(description = "电影ID", example = "1292052")
    private Long movieId;

    @Schema(description = "浏览时间")
    private Date viewTime;
}
