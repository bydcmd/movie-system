package com.movie.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 电影目录筛选请求DTO（用于GET请求）
 * 用于浏览页面的目录筛选，与POST /movies/search（复杂搜索）分离
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "电影目录筛选请求参数（GET请求）")
public class CatalogQueryDTO extends PageRequest {

    @Schema(description = "电影类型筛选（支持多选，逗号分隔）", example = "科幻,动作,冒险")
    private String genres;

    @Schema(description = "地区筛选（支持多选，逗号分隔）", example = "美国,中国大陆")
    private String regions;

    @Schema(description = "语言筛选（支持多选，逗号分隔）", example = "英语,日语")
    private String languages;

    @Schema(description = "最低评分筛选 (1.0-10.0)", example = "8.0")
    @DecimalMin(value = "1.0", message = "最低评分不能小于1")
    @DecimalMax(value = "10.0", message = "最高评分不能大于10")
    private Double minScore;

    @Schema(description = "最高评分筛选 (1.0-10.0)", example = "10.0")
    @DecimalMin(value = "1.0", message = "最低评分不能小于1")
    @DecimalMax(value = "10.0", message = "最高评分不能大于10")
    private Double maxScore;

    @Schema(description = "起始年份范围筛选", example = "2000")
    private Integer startYear;

    @Schema(description = "结束年份范围筛选", example = "2020")
    private Integer endYear;

    @Schema(description = "排序字段: score(评分), year(年份), votes(热度)", example = "score")
    @Pattern(regexp = "^(score|year|votes)$", message = "排序字段非法")
    private String sortBy;

    @Schema(description = "排序方向: desc(降序), asc(升序)", example = "desc", defaultValue = "desc")
    @Pattern(regexp = "^(desc|asc)$", message = "排序方向非法")
    private String sortOrder = "desc";

    /**
     * 将逗号分隔的genres字符串转为List
     */
    public List<String> getGenresList() {
        if (genres == null || genres.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(genres.split(","))
                .map(String::trim)         // 去掉 " Action" 前后的空格
                .filter(s -> !s.isEmpty()) // 过滤掉多余逗号产生的空元素
                .collect(Collectors.toList());
    }

    /**
     * 将逗号分隔的regions字符串转为List
     */
    public List<String> getRegionsList() {
        if (regions == null || regions.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(regions.split(","))
                .map(String::trim)          // 去除每个元素前后的空格
                .filter(s -> !s.isEmpty())  // 过滤掉连续逗号产生的空字符串
                .collect(Collectors.toList()); // 转换为 List
    }

    /**
     * 将逗号分隔的languages字符串转为List
     */
    public List<String> getLanguagesList() {
        if (languages == null || languages.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(languages.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}

