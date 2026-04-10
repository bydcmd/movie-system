package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MovieItemVO;
import com.movie.backend.entity.ViewHistory;
import com.movie.backend.mapper.ViewHistoryMapper;
import com.movie.backend.messaging.event.ViewHistoryEvent;
import com.movie.backend.messaging.outbox.OutboxPublisher;
import com.movie.backend.service.ViewHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;


@Service

public class ViewHistoryServiceImpl implements ViewHistoryService {
    
    @Autowired
    private ViewHistoryMapper viewHistoryMapper;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Override
    public void recordViewHistory(String userId, Long movieId) {
        if (userId == null || movieId == null) {
            return;
        }
        ViewHistory viewHistory = new ViewHistory();
        viewHistory.setUserId(userId);
        viewHistory.setMovieId(movieId);
        viewHistory.setViewTime(new java.util.Date());
        try {
            viewHistoryMapper.insert(viewHistory);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 唯一索引冲突，更新浏览时间
            viewHistoryMapper.updateViewTime(userId, movieId);
        }
        ViewHistoryEvent event = new ViewHistoryEvent(userId, movieId, System.currentTimeMillis(), null);
        outboxPublisher.publishViewHistory(event);
    }


    @Override

    @SuppressWarnings("resource")

    public PageInfo<MovieItemVO> getUserViewHistory(String userId, int page, int size) {

        PageHelper.startPage(page, size);

        List<MovieItemVO> list = viewHistoryMapper.selectHistoryByUserId(userId);
        return new PageInfo<>(list);
    }

    @Override
    public void deleteHistory(String userId, Long historyId) {
        viewHistoryMapper.deleteById(historyId);
    }

    @Override
    @Transactional
    public void deleteBatchHistory(String userId, List<Long> historyIds) {
        if (historyIds == null || historyIds.isEmpty()) {
            throw new IllegalArgumentException("历史记录ID列表不能为空");
        }
        viewHistoryMapper.deleteBatchByIds(userId, historyIds);
    }

    @Override
    public void clearUserHistory(String userId) {
        viewHistoryMapper.deleteAllByUserId(userId);
    }

    @Override
    public int countUserHistory(String userId) {
        return viewHistoryMapper.countByUserId(userId);
    }
}
