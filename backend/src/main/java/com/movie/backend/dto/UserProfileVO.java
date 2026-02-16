package com.movie.backend.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.movie.backend.config.ImagePathSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户个人资料信息")
public class UserProfileVO {

    @Schema(description = "用户ID", example = "movie_fan_01")
    private String id;

    @Schema(description = "用户昵称", example = "电影狂热者")
    private String nickname;

    @Schema(description = "用户头像URL (自动拼接域名)", example = "http://localhost:8080/images/default.png")
    @JsonSerialize(using = ImagePathSerializer.class)
    private String avatar;

    @Schema(description = "个人主页链接", example = "https://douban.com/people/xxx")
    private String url;

    @Schema(description = "电子邮箱", example = "test@movie.com")
    private String email;
}
