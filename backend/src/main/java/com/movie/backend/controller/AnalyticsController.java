package com.movie.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.movie.backend.common.Result;
import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.User;
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

    @Operation(operationId = "getPersonalizedMovies", summary = "猜你喜欢", description = "读取 stats_user_recs 离线个性化推荐；可选登录，未登录/新用户/无离线结果时自动回退到热门日榜。")
    @GetMapping("/personalized")
    public Result<List<Movie>> getPersonalizedMovies(
            @AuthenticationPrincipal User user,
            @Parameter(name = "limit", description = "返回数量，默认10条，最多100条", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {
        String userId = user == null ? null : user.getId();
        return Result.success(analyticsService.getPersonalizedMovies(userId, limit));
    }

    @Deprecated
    @Operation(operationId = "getSimilarMovies", summary = "相似电影（兼容入口）", description = "兼容历史路径。建议改用 /movies/{movieId}/similar。")
    @GetMapping("/movies/{movieId}/similar")
    public Result<List<Movie>> getSimilarMovies(
            @Parameter(name = "movieId", description = "基准电影ID", required = true, example = "1292052")
            @PathVariable @Min(value = 1, message = "电影ID必须大于0") Long movieId,
            @Parameter(name = "type", description = "相似类型：1-内容相似，2-协同过滤", example = "1")
            @RequestParam(required = false) Integer type,
            @Parameter(name = "limit", description = "返回数量，默认10条，最多100条", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {
        if (type != null && type != 1 && type != 2) {
            return Result.fail(400, "无效的相似类型，请选择 1 或 2");
        }
        return Result.success(analyticsService.getSimilarMovies(movieId, type, limit));
    }
}

