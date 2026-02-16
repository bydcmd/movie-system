package com.movie.backend.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.movie.backend.config.ImagePathSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录成功后返回的用户信息")
public class UserVO {

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

    @Schema(description = "用户角色 (0:管理员, 1:普通用户)", example = "1")
    private Integer role;

    @Schema(description = "JWT 访问令牌 (短效)", example = "eyJhbGciOiJIUzUxMiJ9...")
    private String accessToken;

    @Schema(description = "JWT 刷新令牌 (长效)", example = "eyJhbGciOiJIUzUxMiJ9...")
    private String refreshToken;

    @Schema(description = "用户获得的赞数", example = "1024")
    private Integer receivedLikes;

    @Schema(description = "用户发布的影评数", example = "50")
    private Integer commentCount;
}
