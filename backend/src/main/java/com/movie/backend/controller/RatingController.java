package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.movie.backend.common.Result;
import com.movie.backend.dto.BatchIdsDTO;
import com.movie.backend.dto.MyRatingVO;
import com.movie.backend.entity.Rating;
import com.movie.backend.entity.User;
import com.movie.backend.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "rating-management", description = "电影评分相关接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/ratings")
@Validated
@PreAuthorize("isAuthenticated()")
public class RatingController {

    @Autowired
    private RatingService ratingService;

    @Operation(operationId = "updateRating", summary = "提交或更新评分", description = "为指定电影提交评分；若用户已评分则更新原有评分。")
    @PutMapping("/{movieId}")
    public Result<String> updateRating(
            @PathVariable
            @NotNull(message = "电影ID不能为空")
            @Min(value = 1, message = "电影ID必须大于0") Long movieId,
            @RequestParam
            @NotNull(message = "评分不能为空")
            @Min(value = 1, message = "评分必须在 1 到 5 之间")
            @Max(value = 5, message = "评分必须在 1 到 5 之间") Integer rating,
            @AuthenticationPrincipal User user) {
        ratingService.updateRating(user.getId(), movieId, rating);
        return Result.success("评分提交成功");
    }

    @Operation(operationId = "getUserRating", summary = "获取我的电影评分", description = "获取当前登录用户对指定电影的评分；若未评分则返回 null。")
    @GetMapping("/{movieId}")
    public Result<Rating> getUserRating(
            @PathVariable
            @NotNull(message = "电影ID不能为空")
            @Min(value = 1, message = "电影ID必须大于0") Long movieId,
            @AuthenticationPrincipal User user) {
        Rating rating = ratingService.getUserRating(user.getId(), movieId);
        return Result.success(rating);
    }

    @Operation(operationId = "getMyRatings", summary = "获取我的评分列表", description = "获取当前登录用户对所有已评分电影的评分列表。")
    @GetMapping
    public Result<PageInfo<MyRatingVO>> getMyRatings(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size,
            @AuthenticationPrincipal User user) {
        return Result.success(ratingService.getMyRatingVOList(user.getId(), page, size));
    }

    @Operation(operationId = "clearMyRatings", summary = "清空我的全部评分", description = "清空当前登录用户提交的全部评分。")
    @DeleteMapping
    public Result<String> clearMyRatings(@AuthenticationPrincipal User user) {
        ratingService.clearUserRatings(user.getId());
        return Result.success("已清空全部评分");
    }

    @Operation(operationId = "deleteRatingsBatch", summary = "批量删除评分", description = "批量删除当前登录用户的多条评分记录。")
    @PostMapping("/batch-delete")
    public Result<String> deleteRatingsBatch(
            @Valid @RequestBody BatchIdsDTO dto,
            @AuthenticationPrincipal User user) {
        ratingService.deleteRatingsBatch(user.getId(), dto.getIds());
        return Result.success("所选评分已删除");
    }
}
