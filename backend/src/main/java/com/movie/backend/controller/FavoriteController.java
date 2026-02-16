package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.common.Result;
import com.movie.backend.dto.MyFavoriteVO;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.User;
import com.movie.backend.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "收藏管理", description = "电影收藏夹相关接口")
@RestController
@RequestMapping("/favorite")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @Operation(summary = "添加收藏", description = "将电影加入用户的收藏夹")
    @PostMapping("/add")
    public Result<String> addFavorite(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "收藏夹ID，不传则加入默认收藏夹") @RequestParam(required = false) Long folderId,
            @CurrentUser User user) {
        if (folderId == null) {
            favoriteService.addFavorite(user.getId(), movieId);
        } else {
            favoriteService.addFavoriteToFolder(user.getId(), movieId, folderId);
        }
        return Result.success("已添加到收藏");
    }

    @Operation(summary = "取消收藏", description = "将电影从用户的收藏夹移除")
    @DeleteMapping("/remove")
    public Result<String> removeFavorite(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "收藏夹ID，指定则只从该收藏夹移除") @RequestParam(required = false) Long folderId,
            @CurrentUser User user) {
        if (folderId == null) {
            favoriteService.removeFavorite(user.getId(), movieId);
        } else {
            favoriteService.removeFavoriteFromFolder(user.getId(), movieId, folderId);
        }
        return Result.success("已取消收藏");
    }

    @Operation(summary = "查询收藏状态", description = "查询当前用户是否收藏了某部电影")
    @GetMapping("/status")
    public Result<Boolean> isFavorited(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @CurrentUser User user) {
        return Result.success(favoriteService.isFavorited(user.getId(), movieId));
    }

    @Operation(summary = "获取我的收藏列表", description = "分页获取当前用户收藏的所有电影，包含收藏时间")
    @GetMapping("/my")
    public Result<PageInfo<MyFavoriteVO>> getMyFavorites(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @CurrentUser User user) {
        return Result.success(favoriteService.getMyFavoriteVOList(user.getId(), page, size));
    }

    @Operation(summary = "获取收藏夹内电影", description = "分页获取指定收藏夹内的电影列表")
    @GetMapping("/folder/{folderId}")
    public Result<PageInfo<MyFavoriteVO>> getFolderMovies(
            @Parameter(description = "收藏夹ID", required = true) @PathVariable Long folderId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @CurrentUser User user) {
        return Result.success(favoriteService.getFolderMovies(user.getId(), folderId, page, size));
    }

    @Operation(summary = "批量删除收藏", description = "批量移除收藏夹中的多部电影")
    @DeleteMapping("/batch")
    public Result<String> deleteFavoritesBatch(
            @Parameter(description = "电影ID列表", required = true) @RequestBody java.util.List<Long> movieIds,
            @CurrentUser User user) {
        favoriteService.deleteFavoritesBatch(user.getId(), movieIds);
        return Result.success("已批量移除收藏");
    }

    @Operation(summary = "清空收藏夹", description = "清空当前用户的所有收藏")
    @DeleteMapping("/clear")
    public Result<String> clearMyFavorites(@CurrentUser User user) {
        favoriteService.clearUserFavorites(user.getId());
        return Result.success("收藏夹已清空");
    }

    @Operation(summary = "获取收藏总数", description = "统计当前用户的收藏电影总数")
    @GetMapping("/count")
    public Result<Integer> getFavoritesCount(@CurrentUser User user) {
        return Result.success(favoriteService.countUserFavorites(user.getId()));
    }
}