package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.movie.backend.common.Result;
import com.movie.backend.dto.BatchIdsDTO;
import com.movie.backend.dto.MoveFavoritesDTO;
import com.movie.backend.dto.MovieItemVO;
import com.movie.backend.entity.User;
import com.movie.backend.service.FavoriteService;
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

import java.util.List;

@Tag(name = "Favorite Management", description = "电影收藏夹相关接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/favorites")
@Validated
@PreAuthorize("isAuthenticated()")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @Operation(operationId = "addFavorite", summary = "添加收藏", description = "将电影加入用户的收藏夹")
    @PostMapping("/movies/{movieId}")
    public Result<String> addFavorite(
            @Parameter(description = "电影ID", required = true) @PathVariable @Min(1) Long movieId,
            @Parameter(description = "收藏夹ID，不传则加入默认收藏夹") @RequestParam(required = false) @Min(1) Long folderId,
            @AuthenticationPrincipal User user) {
        if (folderId == null) {
            favoriteService.addFavorite(user.getId(), movieId);
        } else {
            favoriteService.addFavoriteToFolder(user.getId(), movieId, folderId);
        }
        return Result.success("已添加到收藏");
    }

    @Operation(operationId = "removeFavorite", summary = "取消收藏", description = "将电影从用户的收藏夹移除")
    @DeleteMapping("/movies/{movieId}")
    public Result<String> removeFavorite(
            @Parameter(description = "电影ID", required = true) @PathVariable @Min(1) Long movieId,
            @Parameter(description = "收藏夹ID，指定则只从该收藏夹移除") @RequestParam(required = false) @Min(1) Long folderId,
            @AuthenticationPrincipal User user) {
        if (folderId == null) {
            favoriteService.removeFavorite(user.getId(), movieId);
        } else {
            favoriteService.removeFavoriteFromFolder(user.getId(), movieId, folderId);
        }
        return Result.success("已取消收藏");
    }

    @Operation(operationId = "isFavorited", summary = "查询收藏状态", description = "查询当前用户是否收藏了某部电影")
    @GetMapping("/movies/{movieId}")
    public Result<Boolean> isFavorited(
            @Parameter(description = "电影ID", required = true) @PathVariable @Min(1) Long movieId,
            @AuthenticationPrincipal User user) {
        return Result.success(favoriteService.isFavorited(user.getId(), movieId));
    }

    @Operation(operationId = "getMyFavorites", summary = "获取我的收藏列表", description = "分页获取当前用户收藏的所有电影，包含收藏时间")
    @GetMapping
    public Result<PageInfo<MovieItemVO>> getMyFavorites(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal User user) {
        return Result.success(favoriteService.getMyFavoriteList(user.getId(), page, size));
    }

    @Operation(operationId = "getFolderMovies", summary = "获取收藏夹内电影", description = "分页获取指定收藏夹内的电影列表")
    @GetMapping("/folders/{folderId}")
    public Result<PageInfo<MovieItemVO>> getFolderMovies(
            @Parameter(description = "收藏夹ID", required = true) @PathVariable @Min(1) Long folderId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal User user) {
        return Result.success(favoriteService.getFolderMovies(user.getId(), folderId, page, size));
    }

    @Operation(operationId = "deleteFavoritesBatch", summary = "批量删除收藏", description = "批量移除收藏夹中的多部电影")
    @PostMapping("/batch-delete")
    public Result<String> deleteFavoritesBatch(
            @Parameter(description = "电影ID列表", required = true) @Valid @RequestBody BatchIdsDTO dto,
            @AuthenticationPrincipal User user) {
        favoriteService.deleteFavoritesBatch(user.getId(), dto.getIds());
        return Result.success("已批量移除收藏");
    }

    @Operation(operationId = "clearMyFavorites", summary = "清空收藏夹", description = "清空当前用户的所有收藏")
    @DeleteMapping
    public Result<String> clearMyFavorites(@AuthenticationPrincipal User user) {
        favoriteService.clearUserFavorites(user.getId());
        return Result.success("收藏夹已清空");
    }

    @Operation(operationId = "getFavoritesCount", summary = "获取收藏总数", description = "统计当前用户的收藏电影总数")
    @GetMapping("/count")
    public Result<Integer> getFavoritesCount(@AuthenticationPrincipal User user) {
        return Result.success(favoriteService.countUserFavorites(user.getId()));
    }

    @Operation(operationId = "moveFavorites", summary = "移动收藏", description = "将电影从一个收藏夹移动到另一个收藏夹，支持单个或批量")
    @PostMapping("/move")
    public Result<String> moveFavorites(
            @Parameter(description = "移动请求参数", required = true) @Valid @RequestBody MoveFavoritesDTO dto,
            @AuthenticationPrincipal User user) {
        favoriteService.moveFavorites(user.getId(), dto.getFromFolderId(), dto.getToFolderId(), dto.getMovieIds());
        return Result.success("移动成功");
    }

    @Operation(operationId = "getMovieFolderIds", summary = "查询电影所在收藏夹列表", description = "查询当前用户的指定电影在哪些收藏夹中，返回收藏夹ID列表")
    @GetMapping("/movies/{movieId}/folders")
    public Result<List<String>> getMovieFolderIds(
            @Parameter(description = "电影ID", required = true) @PathVariable @Min(1) Long movieId,
            @AuthenticationPrincipal User user) {
        return Result.success(
                favoriteService.getMovieFolderIds(user.getId(), movieId).stream()
                        .map(String::valueOf)
                        .toList()
        );
    }
}
