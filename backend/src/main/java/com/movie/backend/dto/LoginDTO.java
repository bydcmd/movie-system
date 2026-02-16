package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
@Schema(description = "用户登录请求参数")
public class LoginDTO {

    @Schema(description = "用户账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "movie_fan_01")
    @NotBlank(message = "账号不能为空")
    private String id;

    @Schema(description = "用户密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotBlank(message = "密码不能为空")
    private String password;
}
