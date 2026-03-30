package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MovieItemVO;

import com.movie.backend.entity.Movie;



public interface FavoriteService {

    void addFavorite(String userId, Long movieId);

    void addFavoriteToFolder(String userId, Long movieId, Long folderId);

    void removeFavorite(String userId, Long movieId);

    

    /**

     * 从指定收藏夹中移除电影

     */

    void removeFavoriteFromFolder(String userId, Long movieId, Long folderId);

    PageInfo<Movie> getUserFavoriteMovies(String userId, int page, int size);

    PageInfo<MovieItemVO> getMyFavoriteList(String userId, int page, int size);

    PageInfo<MovieItemVO> getFolderMovies(String userId, Long folderId, int page, int size);
    boolean isFavorited(String userId, Long movieId);
    
    /**
     * 批量删除用户的收藏记录
     */
    void deleteFavoritesBatch(String userId, java.util.List<Long> movieIds);
    
    /**
     * 清空用户的所有收藏
     */
    void clearUserFavorites(String userId);
    
    /**
     * 统计用户的收藏总数
     */
    int countUserFavorites(String userId);

    /**
     * 移动电影到另一个收藏夹（支持单个或批量）
     */
    void moveFavorites(String userId, Long fromFolderId, Long toFolderId, java.util.List<Long> movieIds);

    /**
     * 查询电影所在的收藏夹ID列表
     * @param userId 用户ID
     * @param movieId 电影ID
     * @return 收藏夹ID列表
     */
    java.util.List<Long> getMovieFolderIds(String userId, Long movieId);
}