package com.movie.backend.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.movie.backend.config.ImagePathSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "公开用户信息（不包含敏感信息）")
public class PublicUserVO {

    @Schema(description = "用户ID", example = "movie_fan_01")
    private String id;

    @Schema(description = "用户昵称", example = "电影狂热者")
    private String nickname;

    @Schema(description = "用户头像URL", example = "http://localhost:8080/images/default.png")
    @JsonSerialize(using = ImagePathSerializer.class)
    private String avatar;

    @Schema(description = "用户豆瓣主页链接", example = "https://douban.com/people/xxx")
    private String url;

    @Schema(description = "用户获得的赞数", example = "1024")
    private Integer receivedLikes;

    @Schema(description = "用户发布的影评数", example = "50")
    private Integer commentCount;
}
