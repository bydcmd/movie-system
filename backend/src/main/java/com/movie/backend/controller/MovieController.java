package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.common.Result;
import com.movie.backend.dto.CatalogQueryDTO;
import com.movie.backend.dto.MovieSearchDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.User;
import com.movie.backend.service.AnalyticsService;
import com.movie.backend.service.MovieService;
import com.movie.backend.service.ViewHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 电影管理控制器
 */
@Tag(name = "电影管理", description = "电影查询、搜索、推荐等相关接口")
@RestController
@RequestMapping("/movie")
@Validated
public class MovieController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ViewHistoryService viewHistoryService;

    @Operation(
            summary = "获取电影详情",
            description = "根据电影ID获取详细信息，包括评分、类型、简介等完整数据。登录用户会自动记录浏览历史。"
    )
    @Parameter(name = "id", description = "电影ID", required = true, example = "1")
    @GetMapping("/detail/{id}")
    public Result<Movie> getDetail(
            @PathVariable @Min(value = 1, message = "电影ID必须大于0") Long id,
            @CurrentUser(required = false) User user) {
        Movie movie = movieService.getDetail(id);
        if (movie == null) {
            return Result.fail(404, "电影不存在");
        }
        
        // 记录浏览历史（仅对已登录用户）
        if (user != null) {
            try {
                viewHistoryService.recordViewHistory(user.getId(), id);
            } catch (Exception e) {
                // 忽略浏览历史记录失败，不影响主流程
            }
        }
        
        return Result.success(movie);
    }

    @Operation(
            summary = "高级搜索（分页）",
            description = "支持关键词、类型、评分、年份等多条件组合搜索，返回分页结果。用于复杂搜索场景。"
    )
    @PostMapping("/search")
    public Result<PageInfo<Movie>> search(@Valid @RequestBody MovieSearchDTO searchDTO) {
        return Result.success(movieService.search(searchDTO));
    }

    @Operation(
            summary = "电影目录筛选（GET，分页）",
            description = "用于浏览页面的简单目录筛选，支持类型、地区、评分、年代等维度的多选筛选。与POST /search分离，使用GET请求更符合RESTful规范。"
    )
    @GetMapping("/catalog")
    public Result<PageInfo<Movie>> getCatalog(@Valid CatalogQueryDTO catalogQuery) {
        return Result.success(movieService.getCatalogMovies(catalogQuery));
    }

    @Operation(
            summary = "获取热门电影",
            description = "直接读取离线分析结果表（stats_hot_movies）中的日榜结果"
    )
    @Parameter(name = "limit", description = "返回数量，默认10条，最多100条", example = "10")
    @GetMapping("/hot")
    public Result<List<Movie>> getHotMovies(
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {
        return Result.success(analyticsService.getHotMoviesByPeriod("DAILY", limit));
    }

    @Operation(
            summary = "按类型筛选电影（分页）",
            description = "根据电影类型（如动作、喜剧、爱情等）筛选电影，支持分页"
    )
    @GetMapping("/genre/{genre}")
    public Result<PageInfo<Movie>> getMoviesByGenre(
            @Parameter(name = "genre", description = "电影类型", required = true, example = "动作")
            @PathVariable String genre,
            @Parameter(name = "page", description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(name = "size", description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(movieService.getMoviesByGenre(genre, page, size));
    }

    @Operation(
            summary = "按年份筛选电影（分页）",
            description = "根据上映年份筛选电影，支持分页"
    )
    @GetMapping("/year/{year}")
    public Result<PageInfo<Movie>> getMoviesByYear(
            @Parameter(name = "year", description = "上映年份", required = true, example = "2023")
            @PathVariable @Min(value = 1900, message = "年份不能早于1900") Integer year,
            @Parameter(name = "page", description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(name = "size", description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(movieService.getMoviesByYear(year, page, size));
    }

    @Operation(
            summary = "获取最新电影（分页）",
            description = "按上映时间倒序排列，获取最新上映的电影"
    )
    @GetMapping("/latest")
    public Result<PageInfo<Movie>> getLatestMovies(
            @Parameter(name = "page", description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(name = "size", description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(movieService.getLatestMovies(page, size));
    }

    @Operation(summary = "获取所有电影类型列表", description = "返回数据库中存在的所有电影类型，用于前端分类展示")
    @GetMapping("/genres")
    public Result<List<String>> getAllGenres() {
        return Result.success(movieService.getAllGenres());
    }

    @Operation(summary = "获取所有地区列表", description = "返回数据库中存在的所有制片地区，用于前端分类展示")
    @GetMapping("/regions")
    public Result<List<String>> getAllRegions() {
        return Result.success(movieService.getAllRegions());
    }

    @Operation(summary = "获取所有上映年份列表", description = "返回数据库中存在的所有上映年份，按倒序排列")
    @GetMapping("/years")
    public Result<List<Integer>> getAllYears() {
        return Result.success(movieService.getAllYears());
    }

    @Operation(summary = "获取筛选分段元数据", description = "返回评分、年份等分段筛选的配置，方便前端渲染筛选菜单")
    @GetMapping("/filter/metadata")
    public Result<Map<String, Object>> getFilterMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        
        // 1. 评分分段
        List<Map<String, Object>> scoreSegments = new ArrayList<>();
        scoreSegments.add(createSegment("9分以上", 9.0, null));
        scoreSegments.add(createSegment("8-9分", 8.0, 9.0));
        scoreSegments.add(createSegment("7-8分", 7.0, 8.0));
        scoreSegments.add(createSegment("6-7分", 6.0, 7.0));
        scoreSegments.add(createSegment("6分以下", null, 6.0));
        metadata.put("scores", scoreSegments);

        // 2. 年份分段 (年代)
        List<Map<String, Object>> yearSegments = new ArrayList<>();
        yearSegments.add(createSegment("2020年代", 2020, null));
        yearSegments.add(createSegment("2010年代", 2010, 2019));
        yearSegments.add(createSegment("2000年代", 2000, 2009));
        yearSegments.add(createSegment("90年代", 1990, 1999));
        yearSegments.add(createSegment("80年代", 1980, 1989));
        yearSegments.add(createSegment("更早", null, 1979));
        metadata.put("eras", yearSegments);

        return Result.success(metadata);
    }

    private Map<String, Object> createSegment(String label, Object min, Object max) {
        Map<String, Object> segment = new HashMap<>();
        segment.put("label", label);
        segment.put("min", min);
        segment.put("max", max);
        return segment;
    }
}


