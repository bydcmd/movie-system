package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通用分页请求基类
 */
@Data
@Schema(description = "通用分页请求参数")
public class PageRequest {

    @Schema(description = "页码 (从1开始)", example = "1", defaultValue = "1")
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Schema(description = "每页显示数量", example = "10", defaultValue = "10")
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer size = 10;
}

