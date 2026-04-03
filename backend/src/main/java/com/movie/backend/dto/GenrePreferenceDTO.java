package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * stats_genre_preference_1d 表查询结果
 */
@Data
@Schema(description = "类型偏好分析数据")
public class GenrePreferenceDTO {
    @Schema(description = "类型名称")
    private String genre;

    @Schema(description = "排名(按热度分数降序)")
    private Integer rankNo;

    @Schema(description = "该类型电影数量")
    private Long movieCnt;

    @Schema(description = "总浏览量")
    private Long viewPv;

    @Schema(description = "独立访客数")
    private Long viewUv;

    @Schema(description = "评分总数")
    private Long ratingCnt;

    @Schema(description = "标记看过总数")
    private Long watchedCnt;

    @Schema(description = "热度分数总和")
    private BigDecimal hotScoreSum;

    @Schema(description = "计算日期(yyyy-MM-dd)")
    private String calcDate;
}
