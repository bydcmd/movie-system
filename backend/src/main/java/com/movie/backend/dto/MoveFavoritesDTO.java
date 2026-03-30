package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "移动收藏请求参数")
public class MoveFavoritesDTO {

    @Schema(description = "源收藏夹ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "源收藏夹ID不能为空")
    @Min(value = 1, message = "收藏夹ID必须大于0")
    private Long fromFolderId;

    @Schema(description = "目标收藏夹ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标收藏夹ID不能为空")
    @Min(value = 1, message = "收藏夹ID必须大于0")
    private Long toFolderId;

    @Schema(description = "电影ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "电影ID列表不能为空")
    private List<
            @NotNull(message = "电影ID不能为空")
            @Min(value = 1, message = "电影ID必须大于0")
            Long> movieIds;
}
