package com.movie.backend.mapper;

import com.movie.backend.dto.MyFavoriteVO;
import com.movie.backend.entity.Favorite;
import com.movie.backend.entity.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FavoriteMapper {
    // 插入收藏记录
    int insert(Favorite favorite);

    // 按用户和电影删除
    int deleteByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId);

    // 批量删除用户的收藏记录
    int deleteBatchByUserAndMovies(@Param("userId") String userId, @Param("movieIds") List<Long> movieIds);

    // 清空用户的所有收藏
    int deleteAllByUserId(@Param("userId") String userId);

    // 删除收藏夹下的所有收藏
    int deleteByFolderId(@Param("folderId") Long folderId);

    // 按用户和电影查询
    Favorite selectByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId);

    // 查询用户的某个电影的所有收藏记录（可能在多个收藏夹中）
    List<Favorite> selectAllByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId);

    // 批量查询用户的电影收藏记录
    List<Favorite> selectBatchByUserAndMovies(@Param("userId") String userId, @Param("movieIds") List<Long> movieIds);

    // 获取用户的收藏电影列表
    List<Movie> selectFavoriteMoviesByUserId(@Param("userId") String userId);

    // 获取用户的收藏列表（包含收藏时间）
    List<MyFavoriteVO> selectMyFavoritesByUserId(@Param("userId") String userId);

    // 统计用户的收藏总数
    int countByUserId(@Param("userId") String userId);

    // 获取指定收藏夹的电影列表
    List<MyFavoriteVO> selectByFolderId(@Param("userId") String userId, @Param("folderId") Long folderId);

    // 按用户、电影和收藏夹删除（用于精确删除）
    int deleteByUserMovieAndFolder(@Param("userId") String userId, @Param("movieId") Long movieId, @Param("folderId") Long folderId);
    
    // 按用户、电影和收藏夹查询（用于检查是否存在）
    Favorite selectByUserMovieAndFolder(@Param("userId") String userId, @Param("movieId") Long movieId, @Param("folderId") Long folderId);
}