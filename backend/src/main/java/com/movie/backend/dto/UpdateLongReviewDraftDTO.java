package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新长评草稿请求参数")
public class UpdateLongReviewDraftDTO {

    @Schema(description = "长评标题", example = "《肖申克的救赎》：由于恐惧而受其所累")
    private String title;

    @Schema(
            description = "长评内容（Tiptap JSON 格式）",
            example = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"这是一部经典的电影...\"}]}]}"
    )
    private String content;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    @Schema(description = "乐观锁版本号", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer version;
}
