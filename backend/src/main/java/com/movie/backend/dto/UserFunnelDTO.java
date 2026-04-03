package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * stats_user_funnel_1d 表查询结果
 */
@Data
@Schema(description = "用户漏斗分析数据")
public class UserFunnelDTO {
    @Schema(description = "活跃用户总数")
    private Long totalActiveUsers;

    @Schema(description = "浏览用户数")
    private Long viewUsers;

    @Schema(description = "评分用户数")
    private Long ratingUsers;

    @Schema(description = "评论用户数")
    private Long commentUsers;

    @Schema(description = "收藏用户数")
    private Long favoriteUsers;

    @Schema(description = "收藏夹操作用户数")
    private Long favoriteFolderActionUsers;

    @Schema(description = "看过用户数")
    private Long watchedUsers;

    @Schema(description = "浏览到评分转化率")
    private BigDecimal viewToRatingRate;

    @Schema(description = "评分到评论转化率")
    private BigDecimal ratingToCommentRate;

    @Schema(description = "评论到收藏转化率")
    private BigDecimal commentToFavoriteRate;

    @Schema(description = "收藏到看过转化率")
    private BigDecimal favoriteToWatchedRate;

    @Schema(description = "计算日期(yyyy-MM-dd)")
    private String calcDate;
}
