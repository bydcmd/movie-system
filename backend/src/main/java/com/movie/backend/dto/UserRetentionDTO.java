package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * stats_user_retention 表查询结果
 */
@Data
@Schema(description = "用户留存分析数据")
public class UserRetentionDTO {
    @Schema(description = "用户注册日期(群组日期)")
    private String cohortDt;

    @Schema(description = "留存天数(1/7/30等)")
    private Integer retentionDay;

    @Schema(description = "群组用户总数")
    private Long cohortUsers;

    @Schema(description = "留存用户数")
    private Long retainedUsers;

    @Schema(description = "留存率")
    private BigDecimal retentionRate;
}
