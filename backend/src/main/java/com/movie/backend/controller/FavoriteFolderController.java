package com.movie.backend.controller;

import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.common.Result;
import com.movie.backend.dto.FavoriteFolderDTO;
import com.movie.backend.dto.FavoriteFolderVO;
import com.movie.backend.entity.FavoriteFolder;
import com.movie.backend.entity.User;
import com.movie.backend.service.FavoriteFolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "收藏夹管理", description = "用户自定义收藏夹相关接口")
@RestController
@RequestMapping("/folder")
public class FavoriteFolderController {
    
    @Autowired
    private FavoriteFolderService favoriteFolderService;
    
    @Operation(summary = "创建收藏夹", description = "创建一个新的自定义收藏夹")
    @PostMapping("/create")
    public Result<FavoriteFolder> createFolder(
            @Valid @RequestBody FavoriteFolderDTO dto,
            @CurrentUser User user) {
        FavoriteFolder folder = favoriteFolderService.createFolder(user.getId(), dto);
        return Result.success(folder);
    }
    
    @Operation(summary = "更新收藏夹", description = "修改收藏夹的名称、描述或公开状态")
    @PutMapping("/update")
    public Result<String> updateFolder(
            @Valid @RequestBody FavoriteFolderDTO dto,
            @CurrentUser User user) {
        favoriteFolderService.updateFolder(user.getId(), dto);
        return Result.success("收藏夹更新成功");
    }
    
    @Operation(summary = "删除收藏夹", description = "删除收藏夹及其下的所有收藏记录")
    @DeleteMapping("/delete/{folderId}")
    public Result<String> deleteFolder(
            @Parameter(description = "收藏夹ID", required = true) @PathVariable Long folderId,
            @CurrentUser User user) {
        favoriteFolderService.deleteFolder(user.getId(), folderId);
        return Result.success("收藏夹已删除");
    }
    
    @Operation(summary = "获取收藏夹详情", description = "根据ID获取收藏夹的详细信息")
    @GetMapping("/{folderId}")
    public Result<FavoriteFolder> getFolderById(
            @Parameter(description = "收藏夹ID", required = true) @PathVariable Long folderId) {
        FavoriteFolder folder = favoriteFolderService.getFolderById(folderId);
        if (folder == null) {
            return Result.fail("收藏夹不存在");
        }
        return Result.success(folder);
    }
    
    @Operation(summary = "获取我的收藏夹列表", description = "获取当前用户的所有收藏夹")
    @GetMapping("/my")
    public Result<List<FavoriteFolderVO>> getMyFolders(@CurrentUser User user) {
        List<FavoriteFolderVO> folders = favoriteFolderService.getUserFolders(user.getId());
        return Result.success(folders);
    }
    
    @Operation(summary = "统计收藏夹数量", description = "获取用户的收藏夹总数")
    @GetMapping("/count")
    public Result<Integer> countFolders(@CurrentUser User user) {
        int count = favoriteFolderService.countUserFolders(user.getId());
        return Result.success(count);
    }
}

