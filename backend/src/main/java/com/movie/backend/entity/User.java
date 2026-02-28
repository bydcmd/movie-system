package com.movie.backend.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.movie.backend.config.ImagePathSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "用户实体")
public class User {
    @Schema(description = "用户ID (唯一账号)", example = "movie_fan_01")
    private String id;

    @Schema(description = "用户昵称", example = "电影狂热者")
    private String nickname;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(description = "加密后的密码 (仅后端使用，不建议直接返回)", hidden = true)
    private String password;

    @Schema(description = "用户头像 URL", example = "http://localhost:8080/images/default_avatar.png")
    @JsonSerialize(using = ImagePathSerializer.class)
    private String avatar;

    @Schema(description = "个人主页/豆瓣链接", example = "https://www.douban.com/people/xxxx")
    private String url;

    @Schema(description = "电子邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "用户角色 (0:管理员, 1:普通用户)", example = "1")
    private Integer role;

    @Schema(description = "账号状态 (0:正常, 1:被禁用/冻结)", example = "0")
    private Integer status;

    @Schema(description = "密码版本号，用于失效旧 Token（修改密码后递增）", example = "1")
    private Integer passwordVersion;

    @Schema(description = "账号创建时间")
    private Date createTime;

    @Schema(description = "最后更新时间")
    private Date updateTime;
}
