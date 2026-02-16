package com.movie.backend.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.movie.backend.config.ImagePathSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "我的评分记录 (包含电影信息)")
public class MyRatingVO {

    @Schema(description = "评分记录ID（使用 movie_id）", example = "1292052")
    private Long id;

    @Schema(description = "电影ID", example = "1292052")
    private Long movieId;

    @Schema(description = "电影名称", example = "肖申克的救赎")
    private String movieName;

    @Schema(description = "电影海报URL", example = "http://localhost:8080/images/poster_01.jpg")
    @JsonSerialize(using = ImagePathSerializer.class)
    private String posterUrl;

    @Schema(description = "我的评分 (1-5)", example = "5")
    private Integer rating;

    @Schema(description = "评分时间", example = "2023-10-01 12:00:00")
    private String ratingTime;

    @Schema(description = "电影简介", example = "20世纪40年代末，小有成就的青年银行家安迪...")
    private String description;
}
