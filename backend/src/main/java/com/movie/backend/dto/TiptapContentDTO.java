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

    /**
     * 创建 TiptapContentDTO
     * @param jsonContent Tiptap JSON 内容
     * @return TiptapContentDTO
     */
    public static TiptapContentDTO fromJson(String jsonContent) {
        TiptapContentDTO dto = new TiptapContentDTO();
        if (jsonContent == null) {
            return dto;
        }
        
        dto.setJsonContent(jsonContent);
        
        // 使用 TiptapJsonValidator 提取纯文本
        String plainText = com.movie.backend.utils.TiptapJsonValidator.extractPlainText(jsonContent);
        dto.setPlainText(plainText);
        dto.setTextLength(plainText.length());
        
        // 生成摘要
        String summary = plainText.length() > 200 
                ? plainText.substring(0, 200).trim() + "..." 
                : plainText.trim();
        dto.setSummary(summary);
        
        // 检查是否包含图片
        dto.setHasImages(jsonContent.contains("\"type\":\"image\""));
        if (dto.isHasImages()) {
            // 简单统计图片数量
            dto.setImageCount(countOccurrences(jsonContent, "\"type\":\"image\""));
        }
        
        return dto;
    }

    /**
     * 统计子字符串出现次数
     */
    private static int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
