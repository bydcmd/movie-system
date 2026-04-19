package com.movie.backend.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "用户自定义收藏夹")
public class FavoriteFolder {
    
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收藏夹ID", example = "1152491045462596111", type = "string")
    private Long id;
    
    @Schema(description = "用户ID", example = "movie_fan_01")
    private String userId;
    
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
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;
}
