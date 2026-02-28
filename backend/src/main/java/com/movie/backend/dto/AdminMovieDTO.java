package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "后台新增/更新电影请求参数")
public class AdminMovieDTO {

    @NotBlank(message = "电影名称不能为空")
    @Size(max = 200, message = "电影名称不能超过200个字符")
    @Schema(description = "电影名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 500, message = "电影别名不能超过500个字符")
    @Schema(description = "电影别名")
    private String alias;

    @Schema(description = "演员列表")
    private List<Map<String, Object>> actors;

    @Size(max = 500, message = "封面地址不能超过500个字符")
    @Schema(description = "封面图片路径")
    private String cover;

    @Schema(description = "导演列表")
    private List<Map<String, Object>> directors;

    @DecimalMin(value = "0.0", message = "评分不能小于0")
    @DecimalMax(value = "10.0", message = "评分不能大于10")
    @Schema(description = "本站评分")
    private Double score;

    @DecimalMin(value = "0.0", message = "豆瓣评分不能小于0")
    @DecimalMax(value = "10.0", message = "豆瓣评分不能大于10")
    @Schema(description = "豆瓣评分")
    private Double doubanScore;

    @Min(value = 0, message = "评分人数不能小于0")
    @Schema(description = "本站评分人数")
    private Integer votes;

    @Min(value = 0, message = "豆瓣评分人数不能小于0")
    @Schema(description = "豆瓣评分人数")
    private Integer doubanVotes;

    @Size(max = 300, message = "类型字段不能超过300个字符")
    @Schema(description = "电影类型")
    private String genres;

    @Size(max = 50, message = "IMDB ID 不能超过50个字符")
    @Schema(description = "IMDB ID")
    private String imdbId;

    @Size(max = 100, message = "语言字段不能超过100个字符")
    @Schema(description = "语言")
    private String languages;

    @Size(max = 50, message = "片长字段不能超过50个字符")
    @Schema(description = "片长")
    private String mins;

    @Size(max = 200, message = "地区字段不能超过200个字符")
    @Schema(description = "制片国家/地区")
    private String regions;

    @Size(max = 100, message = "上映日期字段不能超过100个字符")
    @Schema(description = "上映日期")
    private String releaseDate;

    @Size(max = 20000, message = "剧情简介不能超过20000个字符")
    @Schema(description = "剧情简介")
    private String storyline;

    @Min(value = 1888, message = "上映年份不合法")
    @Max(value = 2100, message = "上映年份不合法")
    @Schema(description = "上映年份")
    private Integer year;

    @Schema(description = "编剧列表")
    private List<Map<String, Object>> writers;

    @Size(max = 500, message = "上榜理由不能超过500个字符")
    @Schema(description = "上榜理由")
    private String reason;
}
