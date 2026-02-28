package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "后台新增/更新影人请求参数")
public class AdminPersonDTO {

    @NotBlank(message = "影人姓名不能为空")
    @Size(max = 100, message = "影人姓名不能超过100个字符")
    @Schema(description = "中文名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 20, message = "性别字段不能超过20个字符")
    @Schema(description = "性别")
    private String sex;

    @Size(max = 100, message = "英文名不能超过100个字符")
    @Schema(description = "英文名")
    private String nameEn;

    @Size(max = 100, message = "中文名(备用)不能超过100个字符")
    @Schema(description = "中文名(备用)")
    private String nameZh;

    @Size(max = 50, message = "出生日期字段不能超过50个字符")
    @Schema(description = "出生日期")
    private String birth;

    @Size(max = 200, message = "出生地字段不能超过200个字符")
    @Schema(description = "出生地")
    private String birthplace;

    @Size(max = 200, message = "职业字段不能超过200个字符")
    @Schema(description = "职业")
    private String profession;

    @Size(max = 20000, message = "影人简介不能超过20000个字符")
    @Schema(description = "影人简介")
    private String biography;

    @Size(max = 500, message = "头像地址不能超过500个字符")
    @Schema(description = "影人头像 URL")
    private String avatar;
}
