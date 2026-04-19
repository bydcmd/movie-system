package com.movie.backend.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.movie.backend.config.ImagePathSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "影人 (演员/导演/编剧) 详细信息")
public class Person {
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "影人ID", example = "1054521", type = "string")
    private Long id;

    @Schema(description = "中文名", example = "蒂姆·罗宾斯")
    private String name;

    @Schema(description = "性别", example = "男")
    private String sex;

    @Schema(description = "英文名", example = "Tim Robbins")
    private String nameEn;

    @Schema(description = "中文名 (备用字段)", example = "蒂姆·罗宾斯")
    private String nameZh;

    @Schema(description = "出生日期", example = "1958-10-16")
    private String birth;

    @Schema(description = "出生地", example = "美国,加利福尼亚,西柯芬")
    private String birthplace;

    @Schema(description = "职业", example = "演员,导演,编剧")
    private String profession;

    @Schema(description = "影人简介", example = "蒂姆·罗宾斯于1958年10月16日生于美国加州...")
    private String biography;

    @Schema(description = "影人照片 URL", example = "http://localhost:8080/images/person_1054521.jpg")
    @JsonSerialize(using = ImagePathSerializer.class)
    private String avatar;
}
