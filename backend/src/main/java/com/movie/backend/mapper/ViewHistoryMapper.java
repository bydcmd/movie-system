package com.movie.backend.mapper;

import com.movie.backend.dto.MyFavoriteVO;
import com.movie.backend.entity.ViewHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ViewHistoryMapper {
    /**
     * 插入浏览历史记录
     */
    int insert(ViewHistory viewHistory);

    /**
     * 查询用户的浏览历史列表（按时间倒序）
     */
    List<MyFavoriteVO> selectHistoryByUserId(@Param("userId") String userId);

    /**
     * 查询用户是否浏览过某部电影
     */
    ViewHistory selectByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId);

    /**
     * 删除用户的某条浏览历史
     */
    int deleteById(@Param("id") Long id);

    /**
     * 批量删除用户的浏览历史
     */
    int deleteBatchByIds(@Param("userId") String userId, @Param("ids") List<Long> ids);

    /**
     * 清空用户的所有浏览历史
     */
    int deleteAllByUserId(@Param("userId") String userId);

    /**
     * 统计用户的浏览历史总数
     */
    int countByUserId(@Param("userId") String userId);

    /**
     * 更新浏览时间（已存在的记录）
     */
    int updateViewTime(@Param("userId") String userId, @Param("movieId") Long movieId);
}
