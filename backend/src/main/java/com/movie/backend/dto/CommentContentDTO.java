package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "评论内容请求参数")
public class CommentContentDTO {

    @NotNull(message = "电影ID不能为空")
    @Schema(description = "电影ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1292052")
    private Long movieId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "短评内容不能超过500字")
    @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "这部电影非常感人，值得一看。")
    private String content;
}
