package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MovieItemVO;

import java.util.List;
import java.util.Map;

public interface WatchedService {
    void addWatched(String userId, Long movieId);

    void removeWatched(String userId, Long movieId);

    boolean isWatched(String userId, Long movieId);

    Map<Long, Boolean> getBatchWatchedStatus(String userId, List<Long> movieIds);

    PageInfo<MovieItemVO> getMyWatchedList(String userId, int page, int size);

    void deleteWatchedBatch(String userId, List<Long> movieIds);

    void clearUserWatched(String userId);

    int countUserWatched(String userId);
}
