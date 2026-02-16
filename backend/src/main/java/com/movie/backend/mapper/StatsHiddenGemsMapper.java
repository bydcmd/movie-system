package com.movie.backend.mapper;

import com.movie.backend.entity.Movie;
import com.movie.backend.entity.StatsHiddenGems;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 冷门佳作推荐榜 Mapper
 */
@Mapper
public interface StatsHiddenGemsMapper {

    /**
     * 插入一条记录
     */
    int insert(StatsHiddenGems record);

    /**
     * 根据日期查询冷门佳作（关联电影信息）
     * @param calcDate 计算日期
     * @param limit 返回数量
     * @return 包含 reason 的电影列表
     */
    List<Movie> selectByDateWithReason(@Param("calcDate") LocalDate calcDate, 
                                       @Param("limit") int limit);

    /**
     * 查询最新一期的冷门佳作（关联电影信息）
     * @param limit 返回数量
     * @return 包含 reason 的电影列表
     */
    List<Movie> selectLatestWithReason(@Param("limit") int limit);

    /**
     * 获取最新的计算日期
     * @return 最新日期
     */
    LocalDate selectLatestCalcDate();

    /**
     * 根据日期删除记录
     */
    int deleteByDate(@Param("calcDate") LocalDate calcDate);

    /**
     * 批量插入
     */
    int batchInsert(@Param("list") List<StatsHiddenGems> list);
}
