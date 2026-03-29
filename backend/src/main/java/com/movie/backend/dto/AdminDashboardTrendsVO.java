package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "管理后台仪表盘趋势数据")
public class AdminDashboardTrendsVO {

    @Schema(description = "近7天新增用户")
    private List<AdminTrendPointVO> userRegistrations;

    @Schema(description = "近7天发布评论")
    private List<AdminTrendPointVO> publishedComments;

    @Schema(description = "近7天新增收藏")
    private List<AdminTrendPointVO> favorites;

    @Schema(description = "近7天新增评分")
    private List<AdminTrendPointVO> ratings;

    @Schema(description = "近7天浏览次数")
    private List<AdminTrendPointVO> views;

    @Schema(description = "近7天新增看过标记")
    private List<AdminTrendPointVO> watchedMovies;
}
