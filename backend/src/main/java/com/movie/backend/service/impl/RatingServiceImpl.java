package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MyRatingVO;
import com.movie.backend.entity.Rating;
import com.movie.backend.mapper.RatingMapper;
import com.movie.backend.service.RatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class RatingServiceImpl implements RatingService {

    @Autowired
    private RatingMapper ratingMapper;

    @Value("${movie.rating.force-update-votes-threshold:50}")
    private Integer forceUpdateVotesThreshold;

    private static final int RATING_REFRESH_BATCH_SIZE = 200;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRating(String userId, Long movieId, Integer rating) {
        if (rating < 1 || rating > 5) {
            throw new RuntimeException("评分必须在 1 到 5 之间");
        }

        LocalDateTime ratingTime = LocalDateTime.now();
        String ratingTimeStr = ratingTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Rating existingRating = ratingMapper.selectByUserAndMovie(userId, movieId);
        boolean ratingChanged = existingRating == null || !Objects.equals(existingRating.getRating(), rating);

        int rows = ratingMapper.upsertByUserAndMovie(userId, movieId, rating, ratingTime);
        if (rows == 0 && existingRating == null) {
            // 并发下可能被其他请求先写入且分值相同，此时无需刷新电影评分
            existingRating = ratingMapper.selectByUserAndMovie(userId, movieId);
            if (existingRating == null) {
                throw new RuntimeException("评分提交失败，请稍后重试");
            }
            ratingChanged = !Objects.equals(existingRating.getRating(), rating);
        }

        if (ratingChanged) {
            refreshMovieRatingSnapshot(movieId);
        }
    }

    @Override
    public Rating getUserRating(String userId, Long movieId) {
        return ratingMapper.selectByUserAndMovie(userId, movieId);
    }

    @Override
    public PageInfo<Rating> getUserRatings(String userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<Rating> list = ratingMapper.selectByUserId(userId);
        return new PageInfo<>(list);
    }

    @Override
    public PageInfo<MyRatingVO> getMyRatingVOList(String userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<MyRatingVO> list = ratingMapper.selectVOByUserId(userId);
        return new PageInfo<>(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearUserRatings(String userId) {
        List<Long> existingMovieIds = ratingMapper.selectMovieIdsByUserId(userId);
        Set<Long> affectedMovieIds = new LinkedHashSet<>();
        if (existingMovieIds != null && !existingMovieIds.isEmpty()) {
            affectedMovieIds.addAll(existingMovieIds);
        }

        ratingMapper.deleteByUserId(userId);
        refreshMovieRatings(affectedMovieIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRatingsBatch(String userId, List<Long> movieIds) {
        if (movieIds != null && !movieIds.isEmpty()) {
            Set<Long> affectedMovieIds = new LinkedHashSet<>(movieIds);
            ratingMapper.deleteBatch(userId, movieIds);
            refreshMovieRatings(affectedMovieIds);
        }
    }

    private void refreshMovieRatings(Set<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return;
        }
        List<Long> batchMovieIds = new ArrayList<>(RATING_REFRESH_BATCH_SIZE);
        for (Long movieId : movieIds) {
            if (movieId == null) {
                continue;
            }
            batchMovieIds.add(movieId);
            if (batchMovieIds.size() >= RATING_REFRESH_BATCH_SIZE) {
                ratingMapper.refreshMovieScoreAndVotesBatch(batchMovieIds, forceUpdateVotesThreshold);
                batchMovieIds.clear();
            }
        }
        if (!batchMovieIds.isEmpty()) {
            ratingMapper.refreshMovieScoreAndVotesBatch(batchMovieIds, forceUpdateVotesThreshold);
        }
    }

    private void refreshMovieRatingSnapshot(Long movieId) {
        if (movieId == null) {
            return;
        }
        ratingMapper.refreshMovieScoreAndVotes(movieId, forceUpdateVotesThreshold);
    }
}
