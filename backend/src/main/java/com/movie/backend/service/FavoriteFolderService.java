package com.movie.backend.service;

import com.movie.backend.dto.FavoriteFolderDTO;
import com.movie.backend.dto.FavoriteFolderVO;
import com.movie.backend.entity.FavoriteFolder;

import java.util.List;

public interface FavoriteFolderService {

    /**
     * 创建收藏夹
     */
    FavoriteFolder createFolder(String userId, FavoriteFolderDTO dto);

    /**
     * 更新收藏夹信息
     */
    void updateFolder(String userId, FavoriteFolderDTO dto);

    /**
     * 删除收藏夹（会同时删除该收藏夹下的所有收藏）
     */
    void deleteFolder(String userId, Long folderId);

    /**
     * 获取收藏夹详情（若无权限访问则抛出 AccessDeniedException）
     */
    FavoriteFolder getFolderById(Long folderId, String viewerUserId);

    /**
     * 获取用户的所有收藏夹列表
     */
    List<FavoriteFolderVO> getUserFolders(String userId);

    /**
     * 统计用户的收藏夹数量
     */
    int countUserFolders(String userId);

    /**
     * 检查收藏夹所有权
     */
    boolean checkFolderOwner(Long folderId, String userId);

    /**
     * 获取或创建用户的默认收藏夹
     * 如果用户没有默认收藏夹则自动创建一个
     */
    FavoriteFolder getOrCreateDefaultFolder(String userId);

    /**
     * 获取用户的默认收藏夹（不自动创建）
     */
    FavoriteFolder getDefaultFolder(String userId);
}
