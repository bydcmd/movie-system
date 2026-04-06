package com.movie.backend.service.impl;

import com.movie.backend.dto.FavoriteFolderDTO;
import com.movie.backend.dto.FavoriteFolderVO;
import com.movie.backend.entity.FavoriteFolder;
import com.movie.backend.mapper.FavoriteMapper;
import com.movie.backend.mapper.FavoriteFolderMapper;
import com.movie.backend.messaging.event.FavoriteFolderActionEvent;
import com.movie.backend.messaging.kafka.KafkaEventPublisher;
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

    @Autowired
    private KafkaEventPublisher kafkaEventPublisher;
    
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
        FavoriteFolderActionEvent event = new FavoriteFolderActionEvent(
                userId,
                folder.getId(),
                folder.getName(),
                folder.getIsPublic(),
                "CREATE",
                null
        );
        kafkaEventPublisher.publishFavoriteFolderActionEvent(event);
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

        FavoriteFolder existing = favoriteFolderMapper.selectById(dto.getId());
        Integer previousPublic = existing != null ? existing.getIsPublic() : null;
        String previousName = existing != null ? existing.getName() : null;

        FavoriteFolder folder = new FavoriteFolder();
        folder.setId(dto.getId());
        folder.setName(dto.getName());
        folder.setDescription(dto.getDescription());
        folder.setIsPublic(dto.getIsPublic());
        
        favoriteFolderMapper.update(folder);

        String currentName = dto.getName() != null ? dto.getName() : previousName;
        Integer currentPublic = dto.getIsPublic() != null ? dto.getIsPublic() : previousPublic;
        String operation = "UPDATE";
        if (previousPublic != null && currentPublic != null && previousPublic == 0 && currentPublic == 1) {
            operation = "SHARE";
        }
        FavoriteFolderActionEvent event = new FavoriteFolderActionEvent(
                userId,
                dto.getId(),
                currentName,
                currentPublic,
                operation,
                null
        );
        kafkaEventPublisher.publishFavoriteFolderActionEvent(event);
    }
    
    @Override
    @Transactional
    public void deleteFolder(String userId, Long folderId) {
        // 检查所有权
        if (!checkFolderOwner(folderId, userId)) {
            throw new IllegalArgumentException("无权删除该收藏夹");
        }

        FavoriteFolder existing = favoriteFolderMapper.selectById(folderId);

        // 删除收藏夹下的所有收藏记录
        favoriteMapper.deleteByFolderId(folderId);
        
        // 删除收藏夹
        favoriteFolderMapper.deleteById(folderId);

        FavoriteFolderActionEvent event = new FavoriteFolderActionEvent(
                userId,
                folderId,
                existing != null ? existing.getName() : null,
                existing != null ? existing.getIsPublic() : null,
                "DELETE",
                null
        );
        kafkaEventPublisher.publishFavoriteFolderActionEvent(event);
    }

    @Override
    @Transactional
    public void deleteFolders(String userId, List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return;
        }
        
        for (Long folderId : folderIds) {
            deleteFolder(userId, folderId);
        }
    }
    
    @Override
    public FavoriteFolder getFolderById(Long folderId, String viewerUserId) {
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null) {
            return null;
        }

        boolean isOwner = viewerUserId != null
                && folder.getUserId() != null
                && folder.getUserId().equals(viewerUserId);
        boolean isPublic = folder.getIsPublic() != null && folder.getIsPublic() == 1;
        if (!isOwner && !isPublic) {
            throw new org.springframework.security.access.AccessDeniedException("无权访问该收藏夹");
        }

        return folder;
    }
    
    @Override
    public List<FavoriteFolderVO> getUserFolders(String userId) {
        // 确保用户有默认收藏夹
        getOrCreateDefaultFolder(userId);
        // 直接查询所有收藏夹（包含默认收藏夹）
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

    @Override
    public FavoriteFolder getOrCreateDefaultFolder(String userId) {
        // 先查询是否已有默认收藏夹
        FavoriteFolder defaultFolder = favoriteFolderMapper.selectDefaultFolder(userId);
        if (defaultFolder != null) {
            return defaultFolder;
        }
        
        // 创建默认收藏夹
        defaultFolder = new FavoriteFolder();
        defaultFolder.setUserId(userId);
        defaultFolder.setName("默认收藏夹");
        defaultFolder.setDescription("系统自动创建的默认收藏夹");
        defaultFolder.setIsPublic(0);
        defaultFolder.setIsDefault(1);
        defaultFolder.setMovieCount(0);
        defaultFolder.setCreateTime(new Date());
        defaultFolder.setUpdateTime(new Date());
        
        favoriteFolderMapper.insertDefaultFolder(defaultFolder);
        return defaultFolder;
    }

    @Override
    public FavoriteFolder getDefaultFolder(String userId) {
        return favoriteFolderMapper.selectDefaultFolder(userId);
    }
}
