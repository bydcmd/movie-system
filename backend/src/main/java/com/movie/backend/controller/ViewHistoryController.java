package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.common.Result;
import com.movie.backend.dto.MyFavoriteVO;
import com.movie.backend.entity.User;
import com.movie.backend.service.ViewHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "浏览历史管理", description = "用户浏览历史相关接口")
@RestController
@RequestMapping("/history")
public class ViewHistoryController {

    @Autowired
    private ViewHistoryService viewHistoryService;

    @Operation(summary = "获取我的浏览历史", description = "分页获取当前用户的浏览历史记录")
    @GetMapping("/my")
    public Result<PageInfo<MyFavoriteVO>> getMyHistory(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @CurrentUser User user) {
        return Result.success(viewHistoryService.getUserViewHistory(user.getId(), page, size));
    }

    @Operation(summary = "删除单条浏览历史", description = "删除指定的浏览历史记录")
    @DeleteMapping("/delete")
    public Result<String> deleteHistory(
            @Parameter(description = "历史记录ID", required = true) @RequestParam Long historyId,
            @CurrentUser User user) {
        viewHistoryService.deleteHistory(user.getId(), historyId);
        return Result.success("已删除浏览历史");
    }

    @Operation(summary = "批量删除浏览历史", description = "批量删除多条浏览历史记录")
    @DeleteMapping("/batch")
    public Result<String> deleteBatchHistory(
            @Parameter(description = "历史记录ID列表", required = true) @RequestBody List<Long> historyIds,
            @CurrentUser User user) {
        viewHistoryService.deleteBatchHistory(user.getId(), historyIds);
        return Result.success("已批量删除浏览历史");
    }

    @Operation(summary = "清空浏览历史", description = "清空当前用户的所有浏览历史")
    @DeleteMapping("/clear")
    public Result<String> clearHistory(@CurrentUser User user) {
        viewHistoryService.clearUserHistory(user.getId());
        return Result.success("浏览历史已清空");
    }

    @Operation(summary = "获取浏览历史总数", description = "统计当前用户的浏览历史总数")
    @GetMapping("/count")
    public Result<Integer> getHistoryCount(@CurrentUser User user) {
        return Result.success(viewHistoryService.countUserHistory(user.getId()));
    }
}
