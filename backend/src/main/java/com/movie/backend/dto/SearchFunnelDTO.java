package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * stats_search_funnel_1d 表查询结果
 */
@Data
@Schema(description = "搜索漏斗分析数据")
public class SearchFunnelDTO {
    @Schema(description = "搜索用户数")
    private Long searchUserCnt;

    @Schema(description = "搜索总次数")
    private Long searchCnt;

    @Schema(description = "有结果的搜索次数")
    private Long searchWithResultCnt;

    @Schema(description = "零结果搜索次数")
    private Long searchZeroResultCnt;

    @Schema(description = "搜索后浏览详情的用户数")
    private Long afterSearchViewUserCnt;

    @Schema(description = "搜索后评分的用户数")
    private Long afterSearchRatingUserCnt;

    @Schema(description = "搜索后收藏的用户数")
    private Long afterSearchFavoriteUserCnt;

    @Schema(description = "搜索后标记看过的用户数")
    private Long afterSearchWatchedUserCnt;

    @Schema(description = "搜索到浏览转化率")
    private BigDecimal searchToViewRate;

    @Schema(description = "搜索到看过转化率")
    private BigDecimal searchToWatchedRate;

    @Schema(description = "搜索到评分转化率")
    private BigDecimal searchToRatingRate;

    @Schema(description = "计算日期(yyyy-MM-dd)")
    private String calcDate;
}
