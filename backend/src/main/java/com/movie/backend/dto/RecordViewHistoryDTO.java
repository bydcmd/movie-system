package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "记录浏览历史请求参数")
public class RecordViewHistoryDTO {

    @Schema(description = "电影ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "电影ID不能为空")
    @Min(value = 1, message = "电影ID必须大于0")
    private Long movieId;
}
