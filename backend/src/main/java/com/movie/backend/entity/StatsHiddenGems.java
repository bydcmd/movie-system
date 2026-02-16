package com.movie.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 冷门佳作推荐榜实体类
 * 对应 stats_hidden_gems 表，每周更新
 */
@Data
@Schema(description = "冷门佳作推荐榜实体")
public class StatsHiddenGems {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "电影ID", example = "1292052")
    private Long movieId;

    @Schema(description = "上榜理由(如: 9.0分但在本站仅100人看过)", example = "9.0分但仅500人评价，绝对的冷门神作")
    private String reason;

    @Schema(description = "计算/上榜日期(用于区分周次)", example = "2026-02-12")
    private LocalDate calcDate;
}
