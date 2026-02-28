package com.movie.backend.utils;

import com.movie.backend.dto.TiptapContentDTO;
import org.springframework.util.StringUtils;

/**
 * 将 Tiptap JSON 内容转换为展示所需的 DTO 信息。
 */
public final class TiptapContentConverter {

    private TiptapContentConverter() {
    }

    public static TiptapContentDTO toDto(String jsonContent) {
        TiptapContentDTO dto = new TiptapContentDTO();
        if (!StringUtils.hasText(jsonContent)) {
            return dto;
        }

        dto.setJsonContent(jsonContent);

        String plainText = TiptapJsonValidator.extractPlainText(jsonContent);
        dto.setPlainText(plainText);
        dto.setTextLength(plainText.length());
        dto.setSummary(TiptapJsonValidator.getSummary(jsonContent, 200));

        dto.setHasImages(jsonContent.contains("\"type\":\"image\""));
        if (dto.isHasImages()) {
            dto.setImageCount(countOccurrences(jsonContent, "\"type\":\"image\""));
        }

        return dto;
    }

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
