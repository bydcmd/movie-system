package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "用户注册请求参数")
public class RegisterDTO {

    @Schema(description = "用户账号 (唯一标识)", requiredMode = Schema.RequiredMode.REQUIRED, example = "movie_fan_01")
    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 20, message = "账号长度需在4-20字符之间")
    private String id;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度不能少于6位")
    private String password;

    @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "电影狂热者")
    @NotBlank(message = "昵称不能为空")
    private String nickname;

    @Schema(description = "电子邮箱", example = "test@movie.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "个人主页/豆瓣主页链接", example = "https://douban.com/people/xxx")
    private String url;
}
