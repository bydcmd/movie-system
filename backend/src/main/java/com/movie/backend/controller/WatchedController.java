package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.movie.backend.common.Result;
import com.movie.backend.dto.BatchIdsDTO;
import com.movie.backend.dto.MovieItemVO;
import com.movie.backend.entity.User;
import com.movie.backend.service.WatchedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Watched Management", description = "用户看过标记相关接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/watched-movies")
@Validated
@PreAuthorize("isAuthenticated()")
public class WatchedController {

    @Autowired
    private WatchedService watchedService;

    @Operation(operationId = "addWatched", summary = "标记看过", description = "将电影标记为当前用户已看过")
    @PostMapping("/{movieId}")
    public Result<String> addWatched(
            @Parameter(description = "电影ID", required = true) @PathVariable @Min(1) Long movieId,
            @AuthenticationPrincipal User user) {
        watchedService.addWatched(user.getId(), movieId);
        return Result.success("已标记为看过");
    }

    @Operation(operationId = "removeWatched", summary = "取消看过", description = "取消当前用户对电影的看过标记")
    @DeleteMapping("/{movieId}")
    public Result<String> removeWatched(
            @Parameter(description = "电影ID", required = true) @PathVariable @Min(1) Long movieId,
            @AuthenticationPrincipal User user) {
        watchedService.removeWatched(user.getId(), movieId);
        return Result.success("已取消看过");
    }

    @Operation(operationId = "isWatched", summary = "查询看过状态", description = "查询当前用户是否看过某部电影")
    @GetMapping("/{movieId}")
    public Result<Boolean> isWatched(
            @Parameter(description = "电影ID", required = true) @PathVariable @Min(1) Long movieId,
            @AuthenticationPrincipal User user) {
        return Result.success(watchedService.isWatched(user.getId(), movieId));
    }

    @Operation(operationId = "getBatchWatchedStatus", summary = "批量查询看过状态", description = "批量查询当前用户对多部电影的看过状态")
    @PostMapping("/status/batch")
    public Result<Map<String, Boolean>> getBatchWatchedStatus(
            @Parameter(description = "电影ID列表", required = true) @Valid @RequestBody BatchIdsDTO dto,
            @AuthenticationPrincipal User user) {
        Map<String, Boolean> statusMap = new java.util.HashMap<>();
        watchedService.getBatchWatchedStatus(user.getId(), dto.getIds())
                .forEach((movieId, watched) -> statusMap.put(String.valueOf(movieId), watched));
        return Result.success(statusMap);
    }

    @Operation(operationId = "getMyWatchedList", summary = "获取我的看过列表", description = "分页获取当前用户标记看过的电影列表")
    @GetMapping
    public Result<PageInfo<MovieItemVO>> getMyWatchedList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal User user) {
        return Result.success(watchedService.getMyWatchedList(user.getId(), page, size));
    }

    @Operation(operationId = "deleteWatchedBatch", summary = "批量取消看过", description = "批量取消当前用户对多部电影的看过标记")
    @PostMapping("/batch-delete")
    public Result<String> deleteWatchedBatch(
            @Parameter(description = "电影ID列表", required = true) @Valid @RequestBody BatchIdsDTO dto,
            @AuthenticationPrincipal User user) {
        watchedService.deleteWatchedBatch(user.getId(), dto.getIds());
        return Result.success("已批量取消看过");
    }

    @Operation(operationId = "clearWatched", summary = "清空看过记录", description = "清空当前用户所有看过标记")
    @DeleteMapping
    public Result<String> clearWatched(@AuthenticationPrincipal User user) {
        watchedService.clearUserWatched(user.getId());
        return Result.success("看过记录已清空");
    }

    @Operation(operationId = "getWatchedCount", summary = "获取看过总数", description = "统计当前用户看过电影总数")
    @GetMapping("/count")
    public Result<Integer> getWatchedCount(@AuthenticationPrincipal User user) {
        return Result.success(watchedService.countUserWatched(user.getId()));
    }
}
