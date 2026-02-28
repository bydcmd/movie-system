package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MovieItemVO;
import com.movie.backend.entity.ViewHistory;
import com.movie.backend.mapper.ViewHistoryMapper;
import com.movie.backend.messaging.event.ViewHistoryEvent;
import com.movie.backend.messaging.kafka.KafkaEventPublisher;
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
    private KafkaEventPublisher kafkaEventPublisher;

    @Override
    public void recordViewHistory(String userId, Long movieId) {
        if (userId == null || movieId == null) {
            return;
        }
        // 异步写入：记录浏览事件，由 Kafka 消费端落库
        long now = System.currentTimeMillis();
        ViewHistoryEvent event = new ViewHistoryEvent(userId, movieId, now);
        kafkaEventPublisher.publishViewHistory(event);
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
