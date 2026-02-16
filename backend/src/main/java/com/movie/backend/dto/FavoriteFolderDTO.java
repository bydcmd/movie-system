package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "创建/更新收藏夹请求参数")
public class FavoriteFolderDTO {
    
    @Schema(description = "收藏夹ID（更新时需要）", example = "1")
    private Long id;
    
    @NotBlank(message = "收藏夹名称不能为空")
    @Size(max = 100, message = "收藏夹名称不能超过100个字符")
    @Schema(description = "收藏夹名称", required = true, example = "我的科幻电影")
    private String name;
    
    @Size(max = 500, message = "收藏夹描述不能超过500个字符")
    @Schema(description = "收藏夹描述", example = "收藏各种科幻题材的电影")
    private String description;
    
    @Schema(description = "是否公开：0-私密, 1-公开", example = "0")
    private Integer isPublic;
}

