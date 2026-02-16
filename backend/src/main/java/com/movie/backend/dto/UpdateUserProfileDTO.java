package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "更新用户个人资料请求参数（支持增量更新）")
public class UpdateUserProfileDTO {

    @Schema(description = "用户昵称", example = "电影狂热者")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    @Schema(description = "用户头像URL", example = "/images/default_avatar.png")
    @Size(max = 500, message = "头像链接长度不能超过500个字符")
    private String avatar;

    @Schema(description = "个人主页链接", example = "https://douban.com/people/xxx")
    @Size(max = 500, message = "个人主页链接长度不能超过500个字符")
    private String url;

    @Schema(description = "电子邮箱", example = "test@movie.com")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;
}

