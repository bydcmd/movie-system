package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台仪表盘总览数据")
public class AdminDashboardOverviewVO {

    @Schema(description = "用户总数", example = "1200")
    private Integer totalUsers;

    @Schema(description = "正常用户数", example = "1100")
    private Integer activeUsers;

    @Schema(description = "冻结用户数", example = "50")
    private Integer frozenUsers;

    @Schema(description = "注销用户数", example = "50")
    private Integer cancelledUsers;

    @Schema(description = "管理员数量", example = "3")
    private Integer adminUsers;

    @Schema(description = "电影总数", example = "5000")
    private Integer totalMovies;

    @Schema(description = "影人总数", example = "18000")
    private Integer totalPeople;

    @Schema(description = "类型总数", example = "32")
    private Integer totalGenres;

    @Schema(description = "地区总数", example = "40")
    private Integer totalRegions;

    @Schema(description = "评论总数（含草稿）", example = "8600")
    private Integer totalComments;

    @Schema(description = "已发布评论数", example = "8200")
    private Integer publishedCommentCount;

    @Schema(description = "草稿评论数", example = "400")
    private Integer draftCommentCount;

    @Schema(description = "已发布短评数", example = "7600")
    private Integer shortCommentCount;

    @Schema(description = "已发布长评数", example = "600")
    private Integer longReviewCount;

    @Schema(description = "评论累计获赞数", example = "25000")
    private Integer totalCommentLikes;

    @Schema(description = "评分总数", example = "9100")
    private Integer totalRatings;

    @Schema(description = "收藏记录总数", example = "5400")
    private Integer totalFavorites;

    @Schema(description = "浏览历史总数", example = "15200")
    private Integer totalViewHistories;

    @Schema(description = "看过记录总数", example = "4700")
    private Integer totalWatchedMovies;
}
