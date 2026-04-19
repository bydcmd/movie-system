package com.movie.backend.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户评分实体")
public class Rating {

    @Schema(description = "用户ID", example = "movie_fan_01")
    private String userId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "电影ID", example = "1292052", type = "string")
    private Long movieId;

    @Schema(description = "评分值 (1-5 星)", example = "5")
    private Integer rating;

    @Schema(description = "评分时间", example = "2023-11-20 14:30:00")
    private LocalDateTime ratingTime;
}
