package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台仪表盘数据")
public class AdminDashboardVO {

    @Schema(description = "兼容字段：用户总数", example = "1200")
    private Integer userCount;

    @Schema(description = "兼容字段：电影总数", example = "5000")
    private Integer movieCount;

    @Schema(description = "兼容字段：已发布评论数", example = "8200")
    private Integer commentCount;

    @Schema(description = "总览数据")
    private AdminDashboardOverviewVO overview;

    @Schema(description = "趋势数据")
    private AdminDashboardTrendsVO trends;
}
