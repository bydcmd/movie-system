package com.movie.backend.controller;

import com.movie.backend.common.Result;
import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.GenrePreferenceDTO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.dto.UserRetentionDTO;
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

@Tag(name = "Analytics", description = "基于离线分析结果的统计接口")
@RestController
@RequestMapping("/analytics")
@Validated
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Operation(operationId = "getTrendingMovies", summary = "获取趋势榜单", description = "获取今日、近7天、近30天和总榜的热门电影，数据来自离线计算后同步到 PostgreSQL 的统计表。")
    @GetMapping("/trending")
    public Result<List<TrendingMovieDTO>> getTrendingMovies(
            @Parameter(name = "period", description = "周期类型: DAILY, WEEKLY, MONTHLY, TOTAL，默认为 DAILY")
            @RequestParam(defaultValue = "DAILY") TrendPeriod period,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 100, message = "返回数量最多为100") int limit) {
        return Result.success(analyticsService.getTrendingMovies(period, limit));
    }

    @Operation(operationId = "getUserRetention", summary = "获取用户留存分析", description = "返回用户留存数据，按群组日期和留存天数分组，数据来自离线计算后同步到 PostgreSQL 的统计表。")
    @GetMapping("/user-retention")
    public Result<List<UserRetentionDTO>> getUserRetention(
            @Parameter(name = "limit", description = "返回数量，默认100条，最多500条", example = "100")
            @RequestParam(defaultValue = "100")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 500, message = "返回数量最多为500") int limit) {
        return Result.success(analyticsService.getUserRetention(limit));
    }

    @Operation(operationId = "getGenrePreference", summary = "获取类型偏好分析", description = "返回各类型的偏好排名数据，按热度分数降序排列，数据来自离线计算后同步到 PostgreSQL 的统计表。")
    @GetMapping("/genre-preference")
    public Result<List<GenrePreferenceDTO>> getGenrePreference(
            @Parameter(name = "limit", description = "返回数量，默认50条，最多200条", example = "50")
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "返回数量至少为1")
            @Max(value = 200, message = "返回数量最多为200") int limit) {
        return Result.success(analyticsService.getGenrePreference(limit));
    }
}

