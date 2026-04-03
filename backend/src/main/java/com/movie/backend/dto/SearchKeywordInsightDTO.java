package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * stats_search_keyword_insights_1d 表查询结果
 */
@Data
@Schema(description = "搜索关键词洞察数据")
public class SearchKeywordInsightDTO {
    @Schema(description = "搜索关键词")
    private String searchKeyword;

    @Schema(description = "排名(按问题分数降序)")
    private Integer rankNo;

    @Schema(description = "搜索次数")
    private Long searchCnt;

    @Schema(description = "搜索用户数")
    private Long searchUserCnt;

    @Schema(description = "零结果次数")
    private Long zeroResultCnt;

    @Schema(description = "零结果率")
    private BigDecimal zeroResultRate;

    @Schema(description = "平均结果数")
    private BigDecimal avgResultCount;

    @Schema(description = "搜索后浏览用户数")
    private Long afterSearchViewUserCnt;

    @Schema(description = "搜索后观看用户数")
    private Long afterSearchWatchUserCnt;

    @Schema(description = "搜索后评分用户数")
    private Long afterSearchRatingUserCnt;

    @Schema(description = "搜索到浏览转化率")
    private BigDecimal searchToViewRate;

    @Schema(description = "浏览到观看转化率")
    private BigDecimal viewToWatchRate;

    @Schema(description = "问题分数(零结果率和低转化加权)")
    private BigDecimal problemScore;
}
