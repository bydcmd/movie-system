package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量ID请求参数")
public class BatchIdsDTO {

    @Schema(description = "ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    @NotEmpty(message = "ID列表不能为空")
    private List<
            @NotNull(message = "ID不能为空")
            @Min(value = 1, message = "ID必须大于0")
            Long> ids;
}
