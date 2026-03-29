package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台趋势数据点")
public class AdminTrendPointVO {

    @Schema(description = "日期", example = "2026-03-25")
    private String date;

    @Schema(description = "当日数值", example = "42")
    private Integer value;
}
