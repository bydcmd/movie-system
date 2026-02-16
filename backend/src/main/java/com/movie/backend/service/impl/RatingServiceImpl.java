package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MyRatingVO;
import com.movie.backend.entity.Rating;
import com.movie.backend.mapper.RatingMapper;
import com.movie.backend.messaging.event.RatingEvent;
import com.movie.backend.messaging.kafka.KafkaEventPublisher;
import com.movie.backend.service.RatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RatingServiceImpl implements RatingService {

    @Autowired
    private RatingMapper ratingMapper;

    @Autowired
    private KafkaEventPublisher kafkaEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitRating(String userId, Long movieId, Integer rating) {
        // 1. 验证评分范围 (1-5)
        if (rating < 1 || rating > 5) {
            throw new RuntimeException("评分必须在 1 到 5 之间");
        }

        Rating ratingEntity = new Rating();
        ratingEntity.setUserId(userId);
        ratingEntity.setMovieId(movieId);
        ratingEntity.setRating(rating);
        ratingEntity.setRatingTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        try {
            // 直接尝试插入，利用数据库的唯一索引 (uk_user_movie) 保证原子性
            ratingMapper.insert(ratingEntity);

            RatingEvent event = new RatingEvent(userId, movieId, rating, "CREATE", ratingEntity.getRatingTime());
            kafkaEventPublisher.publishRatingEvent(event);
        } catch (DuplicateKeyException e) {
            // 4. 捕获唯一索引冲突异常，抛出对应的业务提示
            // 这样既解决了并发问题，又节省了一次查询 IO
            throw new RuntimeException("您已经评价过这部电影了");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRating(String userId, Long movieId, Integer rating) {
        // 1. 验证评分范围 (1-5)
        if (rating < 1 || rating > 5) {
            throw new RuntimeException("评分必须在 1 到 5 之间");
        }

        String ratingTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        int rows = ratingMapper.updateByUserAndMovie(userId, movieId, rating, ratingTime);
        if (rows == 0) {
            throw new RuntimeException("修改失败，您尚未对该电影评分");
        }
        RatingEvent event = new RatingEvent(userId, movieId, rating, "UPDATE", ratingTime);
        kafkaEventPublisher.publishRatingEvent(event);
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
        // 删除用户的所有评分
        ratingMapper.deleteByUserId(userId);
        RatingEvent event = new RatingEvent(userId, null, null, "CLEAR", null);
        kafkaEventPublisher.publishRatingEvent(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRatingsBatch(String userId, List<Long> movieIds) {
        if (movieIds != null && !movieIds.isEmpty()) {
            ratingMapper.deleteBatch(userId, movieIds);
            for (Long movieId : movieIds) {
                RatingEvent event = new RatingEvent(userId, movieId, null, "DELETE", null);
                kafkaEventPublisher.publishRatingEvent(event);
            }
        }
    }
}
