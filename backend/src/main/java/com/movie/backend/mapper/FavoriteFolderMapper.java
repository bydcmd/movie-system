package com.movie.backend.mapper;

import com.movie.backend.dto.FavoriteFolderVO;
import com.movie.backend.entity.FavoriteFolder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FavoriteFolderMapper {

    // 创建收藏夹
    int insert(FavoriteFolder folder);

    // 更新收藏夹信息
    int update(FavoriteFolder folder);

    // 删除收藏夹
    int deleteById(@Param("id") Long id);

    // 根据ID查询收藏夹
    FavoriteFolder selectById(@Param("id") Long id);

    // 查询用户的所有收藏夹
    List<FavoriteFolderVO> selectByUserId(@Param("userId") String userId);

    // 查询用户的收藏夹数量
    int countByUserId(@Param("userId") String userId);

    // 更新收藏夹的电影数量
    int updateMovieCount(@Param("id") Long id, @Param("count") int count);

    // 增加收藏夹的电影数量
    int incrementMovieCount(@Param("id") Long id);

    // 减少收藏夹的电影数量
    int decrementMovieCount(@Param("id") Long id);

    // 批量减少收藏夹的电影数量
    int decrementMovieCountBy(@Param("id") Long id, @Param("count") int count);

    // 检查收藏夹是否属于该用户
    int checkOwner(@Param("id") Long id, @Param("userId") String userId);

    // 获取用户的默认收藏夹
    FavoriteFolder selectDefaultFolder(@Param("userId") String userId);

    // 创建默认收藏夹（返回生成的ID）
    int insertDefaultFolder(FavoriteFolder folder);
}
