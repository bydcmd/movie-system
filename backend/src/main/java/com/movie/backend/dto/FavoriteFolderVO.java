package com.movie.backend.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "收藏夹详情视图对象")
public class FavoriteFolderVO {
    
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收藏夹ID", example = "1152491045462596111", type = "string")
    private Long id;
    
    @Schema(description = "收藏夹名称", example = "我的科幻电影")
    private String name;
    
    @Schema(description = "收藏夹描述", example = "收藏各种科幻题材的电影")
    private String description;
    
    @Schema(description = "是否公开：0-私密, 1-公开", example = "0")
    private Integer isPublic;

    @Schema(description = "是否为默认收藏夹：0-否, 1-是", example = "0")
    private Integer isDefault;

    @Schema(description = "电影数量", example = "15")
    private Integer movieCount;
    
    @Schema(description = "创建时间", example = "2024-01-15 14:30:00")
    private String createTime;
    
    @Schema(description = "更新时间", example = "2024-02-07 10:00:00")
    private String updateTime;
}
