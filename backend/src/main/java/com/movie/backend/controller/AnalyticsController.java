package com.movie.backend.controller;

import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.common.Result;
import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.ColdGemVO;
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

@Tag(name = "数据分析与榜单", description = "基于 Hive/Spark 的数据分析接口")
@RestController
@RequestMapping("/analytics")
@Validated
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Operation(summary = "获取趋势榜单", description = "获取今日、本周、本月的热门电影。底层基于 Hive/Spark 计算。")
    @GetMapping("/trending")
    public Result<List<TrendingMovieDTO>> getTrendingMovies(
            @Parameter(name = "period", description = "周期类型: DAILY, WEEKLY, MONTHLY", required = true)
            @RequestParam String period,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {

        TrendPeriod trendPeriod = TrendPeriod.from(period);
        if (trendPeriod == null) {
            return Result.fail(400, "无效的周期类型，请选择 DAILY, WEEKLY 或 MONTHLY");
        }

        return Result.success(analyticsService.getTrendingMovies(trendPeriod, limit));
    }

    @Operation(summary = "获取冷门佳作", description = "直接读取离线分析结果表 stats_hidden_gems，返回最新一期冷门佳作与上榜理由。")
    @GetMapping("/hidden-gems")
    public Result<List<ColdGemVO>> getHiddenGems(
            @Parameter(name = "limit", description = "返回数量，默认10条，最多100条", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {
        return Result.success(analyticsService.getHiddenGems(limit));
    }

    @Operation(summary = "猜你喜欢", description = "读取 stats_user_recs 离线个性化推荐；新用户或无离线结果时自动回退到热门日榜。")
    @GetMapping("/personalized")
    public Result<List<Movie>> getPersonalizedMovies(
            @CurrentUser(required = false) User user,
            @Parameter(name = "limit", description = "返回数量，默认10条，最多100条", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {
        String userId = user == null ? null : user.getId();
        return Result.success(analyticsService.getPersonalizedMovies(userId, limit));
    }

    @Operation(summary = "相似电影", description = "读取 stats_similar_movies 离线相似度结果，支持按相似类型筛选。")
    @GetMapping("/similar/{movieId}")
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

