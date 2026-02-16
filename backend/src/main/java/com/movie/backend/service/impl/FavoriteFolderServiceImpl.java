package com.movie.backend.service.impl;

import com.movie.backend.dto.FavoriteFolderDTO;
import com.movie.backend.dto.FavoriteFolderVO;
import com.movie.backend.entity.FavoriteFolder;
import com.movie.backend.mapper.FavoriteMapper;
import com.movie.backend.mapper.FavoriteFolderMapper;
import com.movie.backend.service.FavoriteFolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class FavoriteFolderServiceImpl implements FavoriteFolderService {
    
    @Autowired
    private FavoriteFolderMapper favoriteFolderMapper;
    
    @Autowired
    private FavoriteMapper favoriteMapper;
    
    @Override
    public FavoriteFolder createFolder(String userId, FavoriteFolderDTO dto) {
        FavoriteFolder folder = new FavoriteFolder();
        folder.setUserId(userId);
        folder.setName(dto.getName());
        folder.setDescription(dto.getDescription());
        folder.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : 0);
        folder.setMovieCount(0);
        folder.setCreateTime(new Date());
        folder.setUpdateTime(new Date());
        
        favoriteFolderMapper.insert(folder);
        return folder;
    }
    
    @Override
    public void updateFolder(String userId, FavoriteFolderDTO dto) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("收藏夹ID不能为空");
        }
        
        // 检查所有权
        if (!checkFolderOwner(dto.getId(), userId)) {
            throw new IllegalArgumentException("无权修改该收藏夹");
        }
        
        FavoriteFolder folder = new FavoriteFolder();
        folder.setId(dto.getId());
        folder.setName(dto.getName());
        folder.setDescription(dto.getDescription());
        folder.setIsPublic(dto.getIsPublic());
        
        favoriteFolderMapper.update(folder);
    }
    
    @Override
    @Transactional
    public void deleteFolder(String userId, Long folderId) {
        // 检查所有权
        if (!checkFolderOwner(folderId, userId)) {
            throw new IllegalArgumentException("无权删除该收藏夹");
        }
        
        // 删除收藏夹下的所有收藏记录
        favoriteMapper.deleteByFolderId(folderId);
        
        // 删除收藏夹
        favoriteFolderMapper.deleteById(folderId);
    }
    
    @Override
    public FavoriteFolder getFolderById(Long folderId) {
        return favoriteFolderMapper.selectById(folderId);
    }
    
    @Override
    public List<FavoriteFolderVO> getUserFolders(String userId) {
        return favoriteFolderMapper.selectByUserId(userId);
    }
    
    @Override
    public int countUserFolders(String userId) {
        return favoriteFolderMapper.countByUserId(userId);
    }
    
    @Override
    public boolean checkFolderOwner(Long folderId, String userId) {
        return favoriteFolderMapper.checkOwner(folderId, userId) > 0;
    }
}
