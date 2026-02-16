package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "收藏夹详情视图对象")
public class FavoriteFolderVO {
    
    @Schema(description = "收藏夹ID", example = "1")
    private Long id;
    
    @Schema(description = "收藏夹名称", example = "我的科幻电影")
    private String name;
    
    @Schema(description = "收藏夹描述", example = "收藏各种科幻题材的电影")
    private String description;
    
    @Schema(description = "是否公开：0-私密, 1-公开", example = "0")
    private Integer isPublic;
    
    @Schema(description = "电影数量", example = "15")
    private Integer movieCount;
    
    @Schema(description = "创建时间", example = "2024-01-15 14:30:00")
    private String createTime;
    
    @Schema(description = "更新时间", example = "2024-02-07 10:00:00")
    private String updateTime;
}
