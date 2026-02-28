package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "评论及评分请求参数")
public class CommentWithRatingDTO {

    @NotNull(message = "电影ID不能为空")
    @Schema(description = "电影ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1292052")
    private Long movieId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "短评内容不能超过500字")
    @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "剧情节奏很紧凑。")
    private String content;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分必须在 1 到 5 之间")
    @Max(value = 5, message = "评分必须在 1 到 5 之间")
    @Schema(description = "评分(1-5)", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Integer rating;
}
