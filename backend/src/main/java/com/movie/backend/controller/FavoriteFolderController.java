package com.movie.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import com.movie.backend.common.Result;
import com.movie.backend.dto.FavoriteFolderDTO;
import com.movie.backend.dto.FavoriteFolderVO;
import com.movie.backend.entity.FavoriteFolder;
import com.movie.backend.entity.User;
import com.movie.backend.service.FavoriteFolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "Favorite Folder Management", description = "用户自定义收藏夹相关接口")
@RestController
@RequestMapping("/favorite-folders")
@Validated
public class FavoriteFolderController {
    
    @Autowired
    private FavoriteFolderService favoriteFolderService;
    
    @Operation(operationId = "createFavoriteFolder", summary = "创建收藏夹", description = "创建一个新的自定义收藏夹")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<FavoriteFolder> createFolder(
            @Valid @RequestBody FavoriteFolderDTO dto,
            @AuthenticationPrincipal User user) {
        FavoriteFolder folder = favoriteFolderService.createFolder(user.getId(), dto);
        return Result.success(folder);
    }
    
    @Operation(operationId = "updateFavoriteFolder", summary = "更新收藏夹", description = "修改收藏夹的名称、描述或公开状态")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{folderId}")
    public Result<String> updateFolder(
            @Parameter(description = "收藏夹ID", required = true) @PathVariable @Min(1) Long folderId,
            @Valid @RequestBody FavoriteFolderDTO dto,
            @AuthenticationPrincipal User user) {
        dto.setId(folderId);
        favoriteFolderService.updateFolder(user.getId(), dto);
        return Result.success("收藏夹更新成功");
    }
    
    @Operation(operationId = "deleteFavoriteFolder", summary = "删除收藏夹", description = "删除收藏夹及其下的所有收藏记录")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{folderId}")
    public Result<String> deleteFolder(
            @Parameter(description = "收藏夹ID", required = true) @PathVariable @Min(1) Long folderId,
            @AuthenticationPrincipal User user) {
        favoriteFolderService.deleteFolder(user.getId(), folderId);
        return Result.success("收藏夹已删除");
    }
    
    @Operation(operationId = "getFavoriteFolderById", summary = "获取收藏夹详情", description = "根据ID获取收藏夹的详细信息")
    @GetMapping("/{folderId}")
    public Result<FavoriteFolder> getFolderById(
            @Parameter(description = "收藏夹ID", required = true) @PathVariable @Min(1) Long folderId,
            @AuthenticationPrincipal User user) {
        String viewerUserId = user == null ? null : user.getId();
        FavoriteFolder folder = favoriteFolderService.getFolderById(folderId, viewerUserId);
        if (folder == null) {
            return Result.notFound("收藏夹不存在");
        }
        return Result.success(folder);
    }
    
    @Operation(operationId = "getMyFavoriteFolders", summary = "获取我的收藏夹列表", description = "获取当前用户的所有收藏夹")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<List<FavoriteFolderVO>> getMyFolders(@AuthenticationPrincipal User user) {
        List<FavoriteFolderVO> folders = favoriteFolderService.getUserFolders(user.getId());
        return Result.success(folders);
    }
    
    @Operation(operationId = "getFavoriteFolderCount", summary = "统计收藏夹数量", description = "获取用户的收藏夹总数")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/count")
    public Result<Integer> getFolderCount(@AuthenticationPrincipal User user) {
        int count = favoriteFolderService.countUserFolders(user.getId());
        return Result.success(count);
    }
}

