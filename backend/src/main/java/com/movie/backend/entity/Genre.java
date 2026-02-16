package com.movie.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "电影类型实体")
public class Genre {
    @Schema(description = "类型ID", example = "1")
    private Integer id;

    @Schema(description = "类型名称", example = "动作")
    private String name;

    @Schema(description = "英文名称", example = "Action")
    private String nameEn;

    @Schema(description = "类型描述", example = "充满激烈打斗、追逐和爆炸场面的电影")
    private String description;

    @Schema(description = "创建时间")
    private Date createTime;
}
