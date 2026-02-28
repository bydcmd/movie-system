package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 长评提交/更新请求 DTO
 * 支持 Tiptap 编辑器 JSON 格式内容
 */
@Data
@Schema(description = "长评提交/更新请求")
public class LongReviewDTO {

    @NotNull(message = "电影ID不能为空")
    @Schema(description = "电影ID", example = "1292052", required = true)
    private Long movieId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过100字")
    @Schema(description = "长评标题", example = "《肖申克的救赎》：由于恐惧而受其所累", required = true)
    private String title;

    @NotBlank(message = "内容不能为空")
    @Schema(description = "长评内容（Tiptap JSON 格式）", 
            example = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"这是一部经典的电影...\"}]}]}",
            required = true)
    private String content;

}

