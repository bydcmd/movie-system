package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Tiptap 编辑器内容 DTO
 * 用于返回 JSON 格式内容的额外信息
 */
@Data
@Schema(description = "Tiptap 富文本内容信息")
public class TiptapContentDTO {

    @Schema(description = "原始 JSON 内容")
    private String jsonContent;

    @Schema(description = "纯文本内容（去除格式）")
    private String plainText;

    @Schema(description = "内容摘要（前200字）")
    private String summary;

    @Schema(description = "纯文本长度")
    private int textLength;

    @Schema(description = "是否包含图片")
    private boolean hasImages;

    @Schema(description = "图片数量")
    private int imageCount;
}
