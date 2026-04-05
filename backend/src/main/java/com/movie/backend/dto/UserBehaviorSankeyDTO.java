package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * stats_user_behavior_sankey_1d 表查询结果
 */
@Data
@Schema(description = "用户行为桑基图链接数据")
public class UserBehaviorSankeyDTO {
    @Schema(description = "桑基图源节点名称", example = "活跃用户")
    private String sourceNode;

    @Schema(description = "桑基图目标节点名称", example = "搜索用户")
    private String targetNode;

    @Schema(description = "从源节点流向目标节点的用户数", example = "1234")
    private Long userCount;
}
