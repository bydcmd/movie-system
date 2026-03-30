package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.movie.backend.common.Result;
import com.movie.backend.dto.CatalogQueryDTO;
import com.movie.backend.dto.MovieSearchDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.User;
import com.movie.backend.service.AnalyticsService;
import com.movie.backend.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * 电影管理控制器
 */
@Tag(name = "Movie Management", description = "电影查询、搜索、推荐等相关接口")
@RestController
@RequestMapping("/movies")
@Validated
public class MovieController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private AnalyticsService analyticsService;

    @Operation(
            operationId = "getMovieDetail",
            summary = "获取电影详情",
            description = "根据电影ID获取详细信息，包括评分、类型、简介等完整数据。该接口为纯读取接口，不包含写副作用。"
    )
    @Parameter(name = "id", description = "电影ID", required = true, example = "1")
    @GetMapping("/{id:[0-9]+}")
    public Result<Movie> getDetail(@PathVariable @Min(value = 1, message = "电影ID必须大于0") Long id) {
        Movie movie = movieService.getDetail(id);
        if (movie == null) {
            return Result.notFound("电影不存在");
        }

        return Result.success(movie);
    }

    @Operation(
            operationId = "searchMovies",
            summary = "高级搜索（分页）",
            description = "支持关键词、类型、评分、年份等多条件组合搜索，返回分页结果。用于复杂搜索场景。"
    )
    @PostMapping("/search")
    public Result<PageInfo<Movie>> search(
            @Valid @RequestBody(required = false) MovieSearchDTO searchDTO,
            @AuthenticationPrincipal User user) {
        String userId = user != null ? user.getId() : null;
        return Result.success(movieService.search(searchDTO, userId));
    }

    @Operation(
            operationId = "getMovieCatalog",
            summary = "电影目录筛选（GET，分页）",
            description = "用于浏览页面的简单目录筛选，支持类型、地区、语言、评分、年代等维度的多选筛选。与POST /search分离，使用GET请求更符合RESTful规范。"
    )
    @GetMapping
    public Result<PageInfo<Movie>> getCatalog(@Valid CatalogQueryDTO catalogQuery) {
        return Result.success(movieService.getCatalogMovies(catalogQuery));
    }

    @Operation(
            operationId = "getHotMovies",
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

    @Operation(operationId = "getSimilarMoviesByMovie", summary = "获取相似电影", description = "读取离线相似度结果，支持按相似类型筛选。")
    @GetMapping("/{movieId}/similar")
    public Result<List<Movie>> getSimilarMovies(
            @Parameter(name = "movieId", description = "基准电影ID", required = true, example = "1292052")
            @PathVariable @Min(value = 1, message = "电影ID必须大于0") Long movieId,
            @Parameter(name = "type", description = "相似类型：1-内容相似，2-协同过滤，3-ALS隐语义相似", example = "1")
            @RequestParam(required = false) Integer type,
            @Parameter(name = "limit", description = "返回数量，默认10条，最多100条", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {
        if (type != null && type != 1 && type != 2 && type != 3) {
            return Result.fail(400, "无效的相似类型，请选择 1、2 或 3");
        }
        return Result.success(analyticsService.getSimilarMovies(movieId, type, limit));
    }

    @Operation(
            operationId = "getMoviesByGenre",
            summary = "按类型筛选电影（分页）",
            description = "根据电影类型（如动作、喜剧、爱情等）筛选电影，支持分页"
    )
    @GetMapping("/genres/{genre}")
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
            operationId = "getMoviesByYear",
            summary = "按年份筛选电影（分页）",
            description = "根据上映年份筛选电影，支持分页"
    )
    @GetMapping("/years/{year}")
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
            operationId = "getLatestMovies",
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

    @Operation(operationId = "getAllGenres", summary = "获取所有电影类型列表", description = "返回数据库中存在的所有电影类型，用于前端分类展示")
    @GetMapping("/genres")
    public Result<List<String>> getAllGenres() {
        return Result.success(movieService.getAllGenres());
    }

    @Operation(operationId = "getAllRegions", summary = "获取所有地区列表", description = "返回数据库中存在的所有制片地区，用于前端分类展示")
    @GetMapping("/regions")
    public Result<List<String>> getAllRegions() {
        return Result.success(movieService.getAllRegions());
    }

    @Operation(operationId = "getAllLanguages", summary = "获取所有语言列表", description = "返回数据库中存在的所有语言，用于前端分类展示")
    @GetMapping("/languages")
    public Result<List<String>> getAllLanguages() {
        return Result.success(movieService.getAllLanguages());
    }

    @Operation(operationId = "getAllYears", summary = "获取所有上映年份列表", description = "返回数据库中存在的所有上映年份，按倒序排列")
    @GetMapping("/years")
    public Result<List<Integer>> getAllYears() {
        return Result.success(movieService.getAllYears());
    }

    @Operation(operationId = "getFilterMetadata", summary = "获取筛选分段元数据", description = "返回评分、年份等分段筛选的配置，方便前端渲染筛选菜单")
    @GetMapping("/filters/metadata")
    public Result<Map<String, Object>> getFilterMetadata() {
        return Result.success(movieService.getFilterMetadata());
    }

}

