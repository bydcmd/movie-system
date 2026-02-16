package com.movie.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "电影地区实体")
public class Region {
    @Schema(description = "地区ID", example = "1")
    private Integer id;

    @Schema(description = "地区名称", example = "中国大陆")
    private String name;

    @Schema(description = "英文名称", example = "China Mainland")
    private String nameEn;

    @Schema(description = "地区描述", example = "中国大陆地区制作的电影")
    private String description;

    @Schema(description = "创建时间")
    private Date createTime;
}
