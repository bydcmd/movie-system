package com.movie.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.movie.backend.config.ImagePathSerializer;
import com.movie.backend.entity.Comment;
import com.movie.backend.utils.TiptapJsonValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "评论展示对象 (包含用户信息和点赞状态)")
public class CommentVO extends Comment {

    @Schema(description = "评论者昵称", example = "电影狂热者")
    private String userNickname;

    @Schema(description = "评论者ID", example = "user_001")
    private String userId;

    @Schema(description = "评论者头像URL", example = "http://localhost:8080/images/avatar_01.jpg")
    @JsonSerialize(using = ImagePathSerializer.class)
    private String userAvatar;

    @Schema(description = "评论标题")
    private String title;

    @Schema(description = "评论类型 (1:短评, 2:长评)")
    private Integer type;

    @Schema(description = "该用户对电影的评分 (1-5，可能为空)", example = "4")
    private Integer rating;

    @Schema(description = "当前登录用户是否已点赞该评论", example = "true")
    private Boolean isLiked;

    @Schema(description = "评论内容纯文本摘要（长评时有效）", example = "这是一部非常经典的电影，讲述了...")
    private String contentSummary;

    @Schema(description = "评论内容纯文本长度（长评时有效）", example = "1560")
    private Integer contentLength;

    @Schema(description = "是否为 JSON 格式内容（长评时有效）", example = "true")
    private Boolean isJsonContent;

    /**
     * 根据内容类型处理摘要
     * 对于长评的 JSON 内容，提取纯文本摘要
     */
    public void processContentSummary() {
        if (getContent() == null) {
            this.contentSummary = null;
            this.contentLength = 0;
            this.isJsonContent = false;
            return;
        }

        // 检查是否为 JSON 格式
        String content = getContent().trim();
        if (content.startsWith("{") && content.contains("\"type\":\"doc\"")) {
            // 是 Tiptap JSON 格式
            this.isJsonContent = true;
            String plainText = TiptapJsonValidator.extractPlainText(content);
            this.contentLength = plainText.length();
            // 生成摘要（前200字符）
            if (plainText.length() > 200) {
                this.contentSummary = plainText.substring(0, 200).trim() + "...";
            } else {
                this.contentSummary = plainText.trim();
            }
        } else {
            // 普通文本
            this.isJsonContent = false;
            this.contentLength = content.length();
            if (getType() != null && getType() == 2 && content.length() > 200) {
                // 长评生成摘要
                this.contentSummary = content.substring(0, 200).trim() + "...";
            } else {
                this.contentSummary = content;
            }
        }
    }
}
