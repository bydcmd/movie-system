package com.movie.backend.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "用户浏览历史记录")
public class ViewHistory {
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "历史记录ID", example = "1152751459502767564", type = "string")
    private Long id;

    @Schema(description = "用户ID", example = "movie_fan_01")
    private String userId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "电影ID", example = "1292052", type = "string")
    private Long movieId;

    @Schema(description = "浏览时间")
    private Date viewTime;
}
