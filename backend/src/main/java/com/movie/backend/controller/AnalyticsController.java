package com.movie.backend.controller;

import com.movie.backend.common.Result;
import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.GenrePreferenceDTO;
import com.movie.backend.dto.SearchFunnelDTO;
import com.movie.backend.dto.SearchKeywordInsightDTO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.dto.UserFunnelDTO;
import com.movie.backend.dto.UserRetentionDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

@Tag(name = "Analytics", description = "基于 Hive/Spark 的数据分析接口")
@RestController
@RequestMapping("/analytics")
@Validated
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Operation(operationId = "getTrendingMovies", summary = "获取趋势榜单", description = "获取今日、本周、本月和总榜的热门电影。底层基于 Hive/Spark 计算。")
    @GetMapping("/trending")
    public Result<List<TrendingMovieDTO>> getTrendingMovies(
            @Parameter(name = "period", description = "周期类型: DAILY, WEEKLY, MONTHLY, TOTAL，默认为 DAILY")
            @RequestParam(defaultValue = "DAILY") TrendPeriod period,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {
        return Result.success(analyticsService.getTrendingMovies(period, limit));
    }

    @Deprecated
    @Operation(operationId = "getSimilarMovies", summary = "相似电影（兼容入口）", description = "兼容历史路径。建议改用 /movies/{movieId}/similar。")
    @GetMapping("/movies/{movieId}/similar")
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

    @Operation(operationId = "getSearchFunnel", summary = "获取搜索漏斗分析", description = "返回搜索行为的漏斗分析数据，包括搜索次数、转化率等指标。底层基于 Hive/Spark 离线计算。")
    @GetMapping("/search-funnel")
    public Result<SearchFunnelDTO> getSearchFunnel() {
        SearchFunnelDTO result = analyticsService.getSearchFunnel();
        if (result == null) {
            return Result.notFound("暂无搜索漏斗数据");
        }
        return Result.success(result);
    }

    @Operation(operationId = "getSearchKeywordInsights", summary = "获取搜索关键词洞察", description = "返回搜索关键词的问题分析，按问题分数降序排列。底层基于 Hive/Spark 离线计算。")
    @GetMapping("/search-keyword-insights")
    public Result<List<SearchKeywordInsightDTO>> getSearchKeywordInsights(
            @Parameter(name = "limit", description = "返回数量，默认50条，最多200条", example = "50")
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 200, message = "返回数量最多为200") int limit) {
        return Result.success(analyticsService.getSearchKeywordInsights(limit));
    }

    @Operation(operationId = "getUserFunnel", summary = "获取用户漏斗分析", description = "返回用户行为的漏斗分析数据，包括活跃用户数、转化率等指标。底层基于 Hive/Spark 离线计算。")
    @GetMapping("/user-funnel")
    public Result<UserFunnelDTO> getUserFunnel() {
        UserFunnelDTO result = analyticsService.getUserFunnel();
        if (result == null) {
            return Result.notFound("暂无用户漏斗数据");
        }
        return Result.success(result);
    }

    @Operation(operationId = "getUserRetention", summary = "获取用户留存分析", description = "返回用户留存数据，按群组日期和留存天数分组。底层基于 Hive/Spark 离线计算。")
    @GetMapping("/user-retention")
    public Result<List<UserRetentionDTO>> getUserRetention(
            @Parameter(name = "limit", description = "返回数量，默认100条，最多500条", example = "100")
            @RequestParam(defaultValue = "100")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 500, message = "返回数量最多为500") int limit) {
        return Result.success(analyticsService.getUserRetention(limit));
    }

    @Operation(operationId = "getGenrePreference", summary = "获取类型偏好分析", description = "返回各类型的偏好排名数据，按热度分数降序排列。底层基于 Hive/Spark 离线计算。")
    @GetMapping("/genre-preference")
    public Result<List<GenrePreferenceDTO>> getGenrePreference(
            @Parameter(name = "limit", description = "返回数量，默认50条，最多200条", example = "50")
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 200, message = "返回数量最多为200") int limit) {
        return Result.success(analyticsService.getGenrePreference(limit));
    }
}

