package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 电影搜索请求DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "电影高级搜索请求参数")
public class MovieSearchDTO extends PageRequest {

    @Schema(description = "搜索关键词（匹配电影名、别名或简介）", example = "盗梦空间")
    @Size(max = 100, message = "关键词长度不能超过100")
    private String keyword;

    @Schema(description = "电影类型筛选（支持多选）", example = "[\"科幻\", \"动作\"]")
    private List<String> genres;

    @Schema(description = "最低评分筛选 (1.0-10.0)", example = "8.0")
    @DecimalMin(value = "1.0", message = "最低评分不能小于1")
    @DecimalMax(value = "10.0", message = "最高评分不能大于10")
    private Double minScore;

    @Schema(description = "最高评分筛选 (1.0-10.0)", example = "10.0")
    @DecimalMin(value = "1.0", message = "最低评分不能小于1")
    @DecimalMax(value = "10.0", message = "最高评分不能大于10")
    private Double maxScore;

    @Schema(description = "特定年份精确筛选", example = "2010")
    @Pattern(regexp = "^(19|20)\\d{2}$", message = "年份格式不正确，应为4位数字")
    private String year;

    @Schema(description = "起始年份范围筛选", example = "2000")
    private Integer startYear;

    @Schema(description = "结束年份范围筛选", example = "2020")
    private Integer endYear;

    @Schema(description = "地区筛选（支持多选）", example = "[\"美国\", \"中国大陆\"]")
    private List<String> regions;

    @Schema(description = "导演筛选（支持多选，匹配导演名称）", example = "[\"克里斯托弗·诺兰\", \"史蒂文·斯皮尔伯格\"]")
    private List<String> directors;

    @Schema(description = "演员筛选（支持多选，匹配演员名称）", example = "[\"莱昂纳多·迪卡普里奥\", \"汤姆·汉克斯\"]")
    private List<String> actors;

    @Schema(description = "排序字段: score(评分), year(年份), votes(热度)", example = "score")
    @Pattern(regexp = "^(score|year|votes)$", message = "排序字段非法")
    private String sortBy;

    @Schema(description = "排序方向: desc(降序), asc(升序)", example = "desc", defaultValue = "desc")
    @Pattern(regexp = "^(desc|asc)$", message = "排序方向非法")
    private String sortOrder = "desc";
}

