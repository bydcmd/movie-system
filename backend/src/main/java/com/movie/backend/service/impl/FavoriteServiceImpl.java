package com.movie.backend.service.impl;


import com.github.pagehelper.PageHelper;
import com.movie.backend.dto.MovieItemVO;
import com.movie.backend.entity.Favorite;
import com.movie.backend.mapper.FavoriteMapper;
import com.movie.backend.entity.FavoriteFolder;
import com.movie.backend.mapper.FavoriteFolderMapper;
import com.movie.backend.messaging.event.FavoriteEvent;
import com.movie.backend.messaging.kafka.KafkaEventPublisher;
import com.movie.backend.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.movie.backend.entity.Movie;
import com.github.pagehelper.PageInfo;



import java.util.List;

import java.util.Date;



@Service

public class FavoriteServiceImpl implements FavoriteService {
    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private FavoriteFolderMapper favoriteFolderMapper;

    @Autowired
    private KafkaEventPublisher kafkaEventPublisher;

    @Override

    public void addFavorite(String userId, Long movieId) {
        // 检查是否已收藏到默认收藏夹

        Favorite existing = favoriteMapper.selectByUserMovieAndFolder(userId, movieId, 0L);

        if (existing != null) {

            throw new IllegalStateException("该电影已在默认收藏夹中");

        }

        

        Favorite favorite = new Favorite();

        favorite.setUserId(userId);

        favorite.setMovieId(movieId);

        favorite.setFolderId(0L); // 默认收藏夹，使用0代替NULL

        favorite.setCreateTime(new Date());



        favoriteMapper.insert(favorite);
        FavoriteEvent event = new FavoriteEvent(userId, movieId, 0L, "ADD");
        kafkaEventPublisher.publishFavoriteEvent(event);
    }


    @Override

    @Transactional

    public void addFavoriteToFolder(String userId, Long movieId, Long folderId) {
        // 检查是否已收藏到该收藏夹

        Favorite existing = favoriteMapper.selectByUserMovieAndFolder(userId, movieId, folderId);

        if (existing != null) {

            throw new IllegalStateException("该电影已在当前收藏夹中");

        }

        

        Favorite favorite = new Favorite();

        favorite.setUserId(userId);

        favorite.setMovieId(movieId);

        favorite.setFolderId(folderId);

        favorite.setCreateTime(new Date());



        favoriteMapper.insert(favorite);
        FavoriteEvent event = new FavoriteEvent(userId, movieId, folderId, "ADD");
        kafkaEventPublisher.publishFavoriteEvent(event);
        
        // 更新收藏夹的电影数量（folderId=0是默认收藏夹，不需要更新计数）
        if (folderId != null && folderId != 0) {
            favoriteFolderMapper.incrementMovieCount(folderId);
        }

    }



    @Override

    @Transactional

    public void removeFavorite(String userId, Long movieId) {
        // 先查询该电影在哪些收藏夹中

        List<Favorite> favorites = favoriteMapper.selectAllByUserAndMovie(userId, movieId);

        

        // 删除收藏记录

        favoriteMapper.deleteByUserAndMovie(userId, movieId);
        FavoriteEvent event = new FavoriteEvent(userId, movieId, null, "REMOVE");
        kafkaEventPublisher.publishFavoriteEvent(event);
        
        // 更新每个收藏夹的电影数量（folderId=0是默认收藏夹，不需要更新计数）
        for (Favorite favorite : favorites) {
            Long folderId = favorite.getFolderId();
            if (folderId != null && folderId != 0) {

                favoriteFolderMapper.decrementMovieCount(folderId);

            }

        }

    }



    @Override

    @Transactional

    public void removeFavoriteFromFolder(String userId, Long movieId, Long folderId) {
        // 从指定收藏夹中删除
        favoriteMapper.deleteByUserMovieAndFolder(userId, movieId, folderId);
        FavoriteEvent event = new FavoriteEvent(userId, movieId, folderId, "REMOVE");
        kafkaEventPublisher.publishFavoriteEvent(event);
        
        // 更新收藏夹的电影数量
        if (folderId != null && folderId != 0) {
            favoriteFolderMapper.decrementMovieCount(folderId);
        }

    }



    @Override

    public boolean isFavorited(String userId, Long movieId) {

        return favoriteMapper.selectByUserAndMovie(userId, movieId) != null;

    }

    @Override

    @SuppressWarnings("resource")

    public PageInfo<Movie> getUserFavoriteMovies(String userId, int page, int size) {

        PageHelper.startPage(page, size);

        List<Movie> list = favoriteMapper.selectFavoriteMoviesByUserId(userId);

        return new PageInfo<>(list);

    }



    @Override

    @SuppressWarnings("resource")

    public PageInfo<MovieItemVO> getMyFavoriteList(String userId, int page, int size) {

        PageHelper.startPage(page, size);

        List<MovieItemVO> list = favoriteMapper.selectMyFavoritesByUserId(userId);

        return new PageInfo<>(list);

    }



    @Override
    @SuppressWarnings("resource")
    public PageInfo<MovieItemVO> getFolderMovies(String userId, Long folderId, int page, int size) {
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("收藏夹不存在");
        }

        boolean isOwner = folder.getUserId() != null && folder.getUserId().equals(userId);
        boolean isPublic = folder.getIsPublic() != null && folder.getIsPublic() == 1;
        if (!isOwner && !isPublic) {
            throw new IllegalArgumentException("无权访问该收藏夹");
        }

        PageHelper.startPage(page, size);
        List<MovieItemVO> list = favoriteMapper.selectByFolderId(folderId);
        return new PageInfo<>(list);
    }

    @Override
    @Transactional
    public void deleteFavoritesBatch(String userId, List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            throw new IllegalArgumentException("电影ID列表不能为空");
        }
        
        // 先查询所有要删除的收藏记录
        List<Favorite> favorites = favoriteMapper.selectBatchByUserAndMovies(userId, movieIds);
        
        // 删除收藏记录
        favoriteMapper.deleteBatchByUserAndMovies(userId, movieIds);
        for (Long movieId : movieIds) {
            FavoriteEvent event = new FavoriteEvent(userId, movieId, null, "REMOVE");
            kafkaEventPublisher.publishFavoriteEvent(event);
        }
        
        // 统计每个收藏夹减少的电影数量（排除默认收藏夹 folderId=0）
        java.util.Map<Long, Long> folderCountMap = new java.util.HashMap<>();
        for (Favorite favorite : favorites) {
            Long folderId = favorite.getFolderId();
            if (folderId != null && folderId != 0) {
                folderCountMap.put(folderId, folderCountMap.getOrDefault(folderId, 0L) + 1);
            }
        }
        
        // 批量更新收藏夹的电影数量
        for (java.util.Map.Entry<Long, Long> entry : folderCountMap.entrySet()) {
            Long folderId = entry.getKey();
            Long count = entry.getValue();
            // 使用批量减少计数，避免循环调用
            favoriteFolderMapper.decrementMovieCountBy(folderId, count.intValue());
        }
    }

    @Override
    public void clearUserFavorites(String userId) {
        favoriteMapper.deleteAllByUserId(userId);
        FavoriteEvent event = new FavoriteEvent(userId, null, null, "REMOVE");
        kafkaEventPublisher.publishFavoriteEvent(event);
    }

    @Override
    public int countUserFavorites(String userId) {
        return favoriteMapper.countByUserId(userId);
    }
}
