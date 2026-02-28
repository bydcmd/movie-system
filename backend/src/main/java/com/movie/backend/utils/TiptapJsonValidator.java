package com.movie.backend.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Tiptap 富文本编辑器 JSON 内容校验工具
 * Tiptap 基于 ProseMirror，其 JSON 结构遵循 ProseMirror 文档格式
 */
@Slf4j
public class TiptapJsonValidator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Tiptap 支持的节点类型
    private static final Set<String> ALLOWED_NODE_TYPES = new HashSet<>();
    // Tiptap 支持的 marks 类型
    private static final Set<String> ALLOWED_MARK_TYPES = new HashSet<>();

    static {
        // 文档根节点
        ALLOWED_NODE_TYPES.add("doc");
        // 段落
        ALLOWED_NODE_TYPES.add("paragraph");
        // 文本节点
        ALLOWED_NODE_TYPES.add("text");
        // 标题
        ALLOWED_NODE_TYPES.add("heading");
        // 列表项
        ALLOWED_NODE_TYPES.add("listItem");
        // 无序列表
        ALLOWED_NODE_TYPES.add("bulletList");
        // 有序列表
        ALLOWED_NODE_TYPES.add("orderedList");
        // 引用
        ALLOWED_NODE_TYPES.add("blockquote");
        // 代码块
        ALLOWED_NODE_TYPES.add("codeBlock");
        // 水平分割线
        ALLOWED_NODE_TYPES.add("horizontalRule");
        // 硬换行
        ALLOWED_NODE_TYPES.add("hardBreak");
        // 图片
        ALLOWED_NODE_TYPES.add("image");

        // Marks 类型
        ALLOWED_MARK_TYPES.add("bold");
        ALLOWED_MARK_TYPES.add("italic");
        ALLOWED_MARK_TYPES.add("underline");
        ALLOWED_MARK_TYPES.add("strike");
        ALLOWED_MARK_TYPES.add("code");
        ALLOWED_MARK_TYPES.add("link");
    }

    /**
     * 校验 Tiptap JSON 内容
     *
     * @param jsonContent JSON 字符串
     * @return 校验结果
     */
    public static ValidationResult validate(String jsonContent) {
        // 1. 检查是否为空
        if (!StringUtils.hasText(jsonContent)) {
            return ValidationResult.fail("长评内容不能为空");
        }

        // 2. 检查是否为有效的 JSON
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(jsonContent);
        } catch (Exception e) {
            log.warn("Invalid JSON format: {}", e.getMessage());
            return ValidationResult.fail("内容格式错误：不是有效的 JSON 格式");
        }

        // 3. 校验根节点
        ValidationResult rootValidation = validateRootNode(rootNode);
        if (!rootValidation.isValid()) {
            return rootValidation;
        }

        // 4. 递归校验内容节点
        ValidationResult contentValidation = validateContentNodes(rootNode);
        if (!contentValidation.isValid()) {
            return contentValidation;
        }

        // 5. 检查内容长度（估算）
        int textLength = calculateTextLength(rootNode);
        if (textLength > 10000) {
            return ValidationResult.fail("长评内容过长，纯文本长度不能超过 10000 字");
        }

        // 6. 检查内容是否为空
        if (textLength == 0) {
            return ValidationResult.fail("长评内容不能为空");
        }

        return ValidationResult.success(textLength);
    }

    /**
     * 校验根节点
     */
    private static ValidationResult validateRootNode(JsonNode rootNode) {
        // 必须是对象类型
        if (!rootNode.isObject()) {
            return ValidationResult.fail("内容格式错误：根节点必须是对象");
        }

        // 必须有 type 字段且值为 "doc"
        JsonNode typeNode = rootNode.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            return ValidationResult.fail("内容格式错误：缺少 type 字段");
        }

        if (!"doc".equals(typeNode.asText())) {
            return ValidationResult.fail("内容格式错误：根节点 type 必须是 doc");
        }

        // 必须有 content 字段且是数组
        JsonNode contentNode = rootNode.get("content");
        if (contentNode == null || !contentNode.isArray()) {
            return ValidationResult.fail("内容格式错误：缺少 content 数组");
        }

        return ValidationResult.success(0);
    }

    /**
     * 递归校验内容节点
     */
    private static ValidationResult validateContentNodes(JsonNode node) {
        if (node == null) {
            return ValidationResult.success(0);
        }

        // 如果是数组，遍历每个元素
        if (node.isArray()) {
            for (JsonNode child : node) {
                ValidationResult result = validateContentNodes(child);
                if (!result.isValid()) {
                    return result;
                }
            }
            return ValidationResult.success(0);
        }

        // 如果不是对象，返回成功（可能是文本值）
        if (!node.isObject()) {
            return ValidationResult.success(0);
        }

        // 校验节点类型
        JsonNode typeNode = node.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            return ValidationResult.fail("内容格式错误：节点缺少 type 字段");
        }

        String nodeType = typeNode.asText();
        if (!ALLOWED_NODE_TYPES.contains(nodeType)) {
            return ValidationResult.fail("内容格式错误：不支持的节点类型 '" + nodeType + "'");
        }

        // 校验 marks（如果存在）
        JsonNode marksNode = node.get("marks");
        if (marksNode != null) {
            if (!marksNode.isArray()) {
                return ValidationResult.fail("内容格式错误：marks 必须是数组");
            }
            for (JsonNode mark : marksNode) {
                ValidationResult markResult = validateMarkNode(mark);
                if (!markResult.isValid()) {
                    return markResult;
                }
            }
        }

        // 递归校验子内容
        JsonNode contentNode = node.get("content");
        if (contentNode != null) {
            return validateContentNodes(contentNode);
        }

        return ValidationResult.success(0);
    }

    /**
     * 校验 mark 节点
     */
    private static ValidationResult validateMarkNode(JsonNode mark) {
        if (!mark.isObject()) {
            return ValidationResult.fail("内容格式错误：mark 必须是对象");
        }

        JsonNode typeNode = mark.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            return ValidationResult.fail("内容格式错误：mark 缺少 type 字段");
        }

        String markType = typeNode.asText();
        if (!ALLOWED_MARK_TYPES.contains(markType)) {
            return ValidationResult.fail("内容格式错误：不支持的 mark 类型 '" + markType + "'");
        }

        // 校验 link 必须有 attrs.href
        if ("link".equals(markType)) {
            JsonNode attrs = mark.get("attrs");
            if (attrs == null || !attrs.has("href")) {
                return ValidationResult.fail("内容格式错误：link 必须包含 href 属性");
            }
            String href = attrs.get("href").asText();
            if (!isValidUrl(href)) {
                return ValidationResult.fail("内容格式错误：无效的链接地址");
            }
        }

        return ValidationResult.success(0);
    }

    /**
     * 计算纯文本长度（用于限制内容长度）
     */
    private static int calculateTextLength(JsonNode node) {
        if (node == null) {
            return 0;
        }

        int length = 0;

        // 如果是文本节点
        if (node.isObject() && node.has("type") && "text".equals(node.get("type").asText())) {
            JsonNode textNode = node.get("text");
            if (textNode != null && textNode.isTextual()) {
                length += textNode.asText().length();
            }
        }

        // 递归计算子节点
        if (node.isObject()) {
            JsonNode contentNode = node.get("content");
            if (contentNode != null) {
                length += calculateTextLength(contentNode);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                length += calculateTextLength(child);
            }
        }

        return length;
    }

    /**
     * 简单的 URL 校验
     */
    private static boolean isValidUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        // 允许 http, https, mailto 等常见协议
        return url.matches("^(https?://|mailto:|tel:).*") || url.startsWith("/");
    }

    /**
     * 将 Tiptap JSON 转换为纯文本（用于摘要显示）
     */
    public static String extractPlainText(String jsonContent) {
        if (!StringUtils.hasText(jsonContent)) {
            return "";
        }

        try {
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            return extractPlainTextFromNode(rootNode);
        } catch (Exception e) {
            log.warn("Failed to extract plain text: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 从节点提取纯文本
     */
    private static String extractPlainTextFromNode(JsonNode node) {
        if (node == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 如果是文本节点
        if (node.isObject() && node.has("type") && "text".equals(node.get("type").asText())) {
            JsonNode textNode = node.get("text");
            if (textNode != null && textNode.isTextual()) {
                sb.append(textNode.asText());
            }
        }

        // 处理段落和标题（添加换行）
        if (node.isObject() && node.has("type")) {
            String type = node.get("type").asText();
            if ("paragraph".equals(type) || "heading".equals(type) || "listItem".equals(type)) {
                // 递归处理子内容
                JsonNode contentNode = node.get("content");
                if (contentNode != null) {
                    for (JsonNode child : contentNode) {
                        sb.append(extractPlainTextFromNode(child));
                    }
                }
                sb.append("\n");
                return sb.toString();
            }
        }

        // 递归处理子节点
        if (node.isObject()) {
            JsonNode contentNode = node.get("content");
            if (contentNode != null) {
                sb.append(extractPlainTextFromNode(contentNode));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                sb.append(extractPlainTextFromNode(child));
            }
        }

        return sb.toString();
    }

    /**
     * 获取内容摘要（用于列表展示）
     *
     * @param jsonContent JSON 内容
     * @param maxLength   最大长度
     * @return 摘要文本
     */
    public static String getSummary(String jsonContent, int maxLength) {
        return summarizeText(extractPlainText(jsonContent), maxLength);
    }

    /**
     * 对纯文本进行摘要截断。
     *
     * @param plainText 纯文本内容
     * @param maxLength 最大长度
     * @return 摘要文本
     */
    public static String summarizeText(String plainText, int maxLength) {
        if (!StringUtils.hasText(plainText) || maxLength <= 0) {
            return "";
        }
        String normalized = plainText.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim() + "...";
    }

    /**
     * 校验结果类 - 用于 JSON 序列化
     */
    public static class ValidationResult {
        @JsonProperty("valid")
        private final boolean valid;
        
        @JsonProperty("message")
        private final String message;
        
        @JsonProperty("textLength")
        private final int textLength;

        private ValidationResult(boolean valid, String message, int textLength) {
            this.valid = valid;
            this.message = message;
            this.textLength = textLength;
        }

        public static ValidationResult success(int textLength) {
            return new ValidationResult(true, null, textLength);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message, 0);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public int getTextLength() {
            return textLength;
        }
    }
}
