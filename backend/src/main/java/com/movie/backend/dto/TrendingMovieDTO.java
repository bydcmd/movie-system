package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "趋势榜单返回项")
public class TrendingMovieDTO {
    @Schema(description = "电影ID")
    private Long movieId;

    @Schema(description = "电影名称")
    private String name;

    @Schema(description = "封面图片路径")
    private String cover;

    @Schema(description = "豆瓣评分")
    private Double doubanScore;

    @Schema(description = "上映年份")
    private Integer year;

    @Schema(description = "制片国家/地区")
    private String regions;

    @Schema(description = "电影类型(逗号分隔)")
    private String genres;

    @Schema(description = "热度分值")
    private Double hotScore;

    @Schema(description = "计算日期(yyyy-MM-dd)")
    private String calcDate;

    @Schema(description = "统计周期")
    private String period;

    @Schema(description = "排名，从1开始")
    private Integer rank;
}
