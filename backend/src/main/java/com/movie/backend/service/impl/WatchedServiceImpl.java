package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MovieItemVO;
import com.movie.backend.entity.Watched;
import com.movie.backend.mapper.WatchedMapper;
import com.movie.backend.service.WatchedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WatchedServiceImpl implements WatchedService {

    @Autowired
    private WatchedMapper watchedMapper;

    @Override
    public void addWatched(String userId, Long movieId) {
        Watched existing = watchedMapper.selectByUserAndMovie(userId, movieId);
        if (existing != null) {
            throw new IllegalStateException("已标记为看过");
        }
        Watched watched = new Watched();
        watched.setUserId(userId);
        watched.setMovieId(movieId);
        watched.setCreateTime(new Date());
        watchedMapper.insert(watched);
    }

    @Override
    public void removeWatched(String userId, Long movieId) {
        watchedMapper.deleteByUserAndMovie(userId, movieId);
    }

    @Override
    public boolean isWatched(String userId, Long movieId) {
        return watchedMapper.selectByUserAndMovie(userId, movieId) != null;
    }

    @Override
    public Map<Long, Boolean> getBatchWatchedStatus(String userId, List<Long> movieIds) {
        Map<Long, Boolean> result = new HashMap<>();
        if (movieIds == null || movieIds.isEmpty()) {
            return result;
        }
        List<Watched> watchedList = watchedMapper.selectBatchByUserAndMovies(userId, movieIds);
        Set<Long> watchedIds = new HashSet<>();
        for (Watched watched : watchedList) {
            watchedIds.add(watched.getMovieId());
        }
        for (Long movieId : movieIds) {
            result.put(movieId, watchedIds.contains(movieId));
        }
        return result;
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<MovieItemVO> getMyWatchedList(String userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<MovieItemVO> list = watchedMapper.selectMyWatchedByUserId(userId);
        return new PageInfo<>(list);
    }

    @Override
    @Transactional
    public void deleteWatchedBatch(String userId, List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            throw new IllegalArgumentException("电影ID列表不能为空");
        }
        watchedMapper.deleteBatchByUserAndMovies(userId, movieIds);
    }

    @Override
    public void clearUserWatched(String userId) {
        watchedMapper.deleteAllByUserId(userId);
    }

    @Override
    public int countUserWatched(String userId) {
        return watchedMapper.countByUserId(userId);
    }
}
