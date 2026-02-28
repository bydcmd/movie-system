package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.movie.backend.common.Result;
import com.movie.backend.dto.BatchIdsDTO;
import com.movie.backend.dto.MovieItemVO;
import com.movie.backend.dto.RecordViewHistoryDTO;
import com.movie.backend.entity.User;
import com.movie.backend.service.ViewHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;


@Tag(name = "View History Management", description = "用户浏览历史相关接口")

@SecurityRequirement(name = "BearerAuth")

@RestController

@RequestMapping("/view-histories")
@Validated
@PreAuthorize("isAuthenticated()")
public class ViewHistoryController {



    @Autowired
    private ViewHistoryService viewHistoryService;

    @Operation(operationId = "recordViewHistory", summary = "记录浏览行为", description = "显式记录当前用户浏览了某部电影")
    @PostMapping
    public Result<String> recordViewHistory(@Valid @RequestBody RecordViewHistoryDTO dto, @AuthenticationPrincipal User user) {
        viewHistoryService.recordViewHistory(user.getId(), dto.getMovieId());
        return Result.success("浏览历史记录成功");
    }

    @Operation(operationId = "getMyHistory", summary = "获取我的浏览历史", description = "分页获取当前用户的浏览历史记录")
    @GetMapping
    public Result<PageInfo<MovieItemVO>> getMyHistory(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal User user) {
        return Result.success(viewHistoryService.getUserViewHistory(user.getId(), page, size));
    }

    @Operation(operationId = "deleteHistory", summary = "删除单条浏览历史", description = "删除指定的浏览历史记录")
    @DeleteMapping("/{historyId}")
    public Result<String> deleteHistory(
            @Parameter(description = "历史记录ID", required = true) @PathVariable @Min(1) Long historyId,
            @AuthenticationPrincipal User user) {
        viewHistoryService.deleteHistory(user.getId(), historyId);
        return Result.success("已删除浏览历史");
    }

    @Operation(operationId = "deleteHistoryBatch", summary = "批量删除浏览历史", description = "批量删除多条浏览历史记录")
    @PostMapping("/batch-delete")
    public Result<String> deleteHistoryBatch(
            @Parameter(description = "历史记录ID列表", required = true) @Valid @RequestBody BatchIdsDTO dto,
            @AuthenticationPrincipal User user) {
        viewHistoryService.deleteBatchHistory(user.getId(), dto.getIds());
        return Result.success("已批量删除浏览历史");
    }

    @Operation(operationId = "clearHistory", summary = "清空浏览历史", description = "清空当前用户的所有浏览历史")
    @DeleteMapping
    public Result<String> clearHistory(@AuthenticationPrincipal User user) {
        viewHistoryService.clearUserHistory(user.getId());
        return Result.success("浏览历史已清空");
    }

    @Operation(operationId = "getHistoryCount", summary = "获取浏览历史总数", description = "统计当前用户的浏览历史总数")
    @GetMapping("/count")
    public Result<Integer> getHistoryCount(@AuthenticationPrincipal User user) {
        return Result.success(viewHistoryService.countUserHistory(user.getId()));
    }
}
