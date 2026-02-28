package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MovieItemVO;

import java.util.List;

public interface ViewHistoryService {
    /**
     * 记录用户浏览历史
     */
    void recordViewHistory(String userId, Long movieId);

    /**
     * 获取用户的浏览历史列表（分页）
     */
    PageInfo<MovieItemVO> getUserViewHistory(String userId, int page, int size);

    /**
     * 删除单条浏览历史
     */
    void deleteHistory(String userId, Long historyId);

    /**
     * 批量删除浏览历史
     */
    void deleteBatchHistory(String userId, List<Long> historyIds);

    /**
     * 清空用户的所有浏览历史
     */
    void clearUserHistory(String userId);

    /**
     * 统计用户的浏览历史总数
     */
    int countUserHistory(String userId);
}
