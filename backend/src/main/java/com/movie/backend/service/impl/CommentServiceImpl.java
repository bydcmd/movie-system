package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.CommentVO;
import com.movie.backend.dto.LongReviewDTO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.CommentLike;
import com.movie.backend.mapper.CommentLikeMapper;
import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.messaging.event.CommentEvent;
import com.movie.backend.messaging.event.CommentLikeEvent;
import com.movie.backend.messaging.kafka.KafkaEventPublisher;
import com.movie.backend.service.CommentService;
import com.movie.backend.service.RatingService;
import com.movie.backend.utils.TiptapJsonValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private CommentLikeMapper commentLikeMapper;

    @Autowired
    private RatingService ratingService;

    @Autowired
    private KafkaEventPublisher kafkaEventPublisher;

    @Override
    public PageInfo<Comment> getCommentsByMovieId(Long movieId, int page, int size) {
        PageHelper.startPage(page, size);
        try {
            List<Comment> list = commentMapper.selectByMovieId(movieId);
            return new PageInfo<>(list);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    public PageInfo<CommentVO> getCommentsWithRatingByMovieId(Long movieId, String currentUserId, int page, int size) {
        PageHelper.startPage(page, size);
        try {
            List<CommentVO> list = commentMapper.selectWithRatingByMovieId(movieId, currentUserId);
            // 处理内容摘要
            for (CommentVO vo : list) {
                vo.processContentSummary();
            }
            return new PageInfo<>(list);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitComment(String userId, Long movieId, String content) {
        // 参数校验
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("短评内容不能超过500字");
        }

        // 短评逻辑：type = 1
        // 使用数据库唯一索引保证幂等性，捕获唯一键冲突异常
        try {
            Comment comment = new Comment();
            comment.setUserId(userId);
            comment.setMovieId(movieId);
            comment.setContent(content.trim());
            comment.setType(1); // 1 = 短评
            comment.setVotes(0);
            comment.setVersion(0); // 初始版本号
            comment.setCommentTime(new java.util.Date());

            commentMapper.insert(comment);
            // 插入后 comment.getId() 会自动填充数据库生成的自增ID
            CommentEvent event = new CommentEvent(userId, movieId, comment.getId(), 1, "CREATE", content.length());
            kafkaEventPublisher.publishCommentEvent(event);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引冲突，说明用户已发表过短评
            throw new RuntimeException("您已经发表过短评了，无法重复发布");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitLongReview(String userId, Long movieId, String title, String content) {
        // 参数校验
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("长评标题不能为空");
        }
        if (title.length() > 100) {
            throw new IllegalArgumentException("标题不能超过100字");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("长评内容不能为空");
        }
        if (content.length() > 5000) {
            throw new IllegalArgumentException("长评内容不能超过5000字");
        }

        // 长评逻辑：type = 2
        // 使用数据库唯一索引保证幂等性，捕获唯一键冲突异常
        try {
            Comment comment = new Comment();
            comment.setUserId(userId);
            comment.setMovieId(movieId);
            comment.setTitle(title.trim());
            comment.setContent(content.trim());
            comment.setType(2);      // 2 = 长评
            comment.setVotes(0);
            comment.setVersion(0); // 初始版本号
            comment.setCommentTime(new java.util.Date());

            commentMapper.insert(comment);
            CommentEvent event = new CommentEvent(userId, movieId, comment.getId(), 2, "CREATE", content.length());
            kafkaEventPublisher.publishCommentEvent(event);
            // 插入后 comment.getId() 会自动填充数据库生成的自增ID
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引冲突，说明用户已发表过长评
            throw new RuntimeException("您已经发表过长评了，如需修改请前往个人中心");
        }
    }

    @Override
    public Comment getUserComment(String userId, Long movieId) {
        return commentMapper.selectByUserAndMovie(userId, movieId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCommentWithRating(String userId, Long movieId, String content, Integer rating) {
        // 参数校验
        if (rating == null) {
            throw new IllegalArgumentException("评分不能为空");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在 1 到 5 之间");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("短评内容不能超过500字");
        }
        
        // 1. 修改评分
        ratingService.updateRating(userId, movieId, rating);

        // 2. 修改评论
        int rows = commentMapper.updateByUserAndMovie(userId, movieId, content.trim(), new java.util.Date());
        if (rows == 0) {
            throw new RuntimeException("修改失败，您尚未对该电影发表评论");
        }
        CommentEvent event = new CommentEvent(userId, movieId, null, 1, "UPDATE", content.length());
        kafkaEventPublisher.publishCommentEvent(event);
    }

    @Override
    @Transactional
    public void updateComment(String userId, Long movieId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("短评内容不能超过500字");
        }

        int rows = commentMapper.updateByUserAndMovie(userId, movieId, content.trim(), new java.util.Date());
        if (rows == 0) {
            throw new RuntimeException("修改失败，您尚未对该电影发表评论");
        }
        CommentEvent event = new CommentEvent(userId, movieId, null, 1, "UPDATE", content.length());
        kafkaEventPublisher.publishCommentEvent(event);
    }

    @Override
    @Transactional
    public void updateLongReview(String userId, Long movieId, String title, String content) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("长评标题不能为空");
        }
        if (title.length() > 100) {
            throw new IllegalArgumentException("标题不能超过100字");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("长评内容不能为空");
        }

        int rows = commentMapper.updateLongComment(userId, movieId, title.trim(), content.trim(), new java.util.Date());
        if (rows == 0) {
            throw new RuntimeException("修改失败，您尚未对该电影发表长评");
        }
        CommentEvent event = new CommentEvent(userId, movieId, null, 2, "UPDATE", content.length());
        kafkaEventPublisher.publishCommentEvent(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitLongReviewJson(String userId, LongReviewDTO dto) {
        // 1. 校验标题
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new IllegalArgumentException("长评标题不能为空");
        }
        if (dto.getTitle().length() > 100) {
            throw new IllegalArgumentException("标题不能超过100字");
        }

        // 2. 校验 JSON 内容格式（Tiptap 格式）
        TiptapJsonValidator.ValidationResult validationResult = 
                TiptapJsonValidator.validate(dto.getContent());
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getMessage());
        }

        // 3. 存储 JSON 内容
        try {
            Comment comment = new Comment();
            comment.setUserId(userId);
            comment.setMovieId(dto.getMovieId());
            comment.setTitle(dto.getTitle().trim());
            comment.setContent(dto.getContent().trim()); // 存储原始 JSON 字符串
            comment.setType(2); // 2 = 长评
            comment.setVotes(0);
            comment.setVersion(0);
            comment.setCommentTime(new java.util.Date());

            commentMapper.insert(comment);
            CommentEvent event = new CommentEvent(userId, dto.getMovieId(), comment.getId(), 2, "CREATE", dto.getContent().length());
            kafkaEventPublisher.publishCommentEvent(event);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new RuntimeException("您已经发表过长评了，如需修改请前往个人中心");
        }
    }

    @Override
    @Transactional
    public void updateLongReviewJson(String userId, LongReviewDTO dto) {
        // 1. 校验标题
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new IllegalArgumentException("长评标题不能为空");
        }
        if (dto.getTitle().length() > 100) {
            throw new IllegalArgumentException("标题不能超过100字");
        }

        // 2. 校验 JSON 内容格式（Tiptap 格式）
        TiptapJsonValidator.ValidationResult validationResult = 
                TiptapJsonValidator.validate(dto.getContent());
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getMessage());
        }

        // 3. 更新长评
        int rows = commentMapper.updateLongComment(
                userId, 
                dto.getMovieId(), 
                dto.getTitle().trim(), 
                dto.getContent().trim(), // 存储原始 JSON 字符串
                new java.util.Date()
        );
        if (rows == 0) {
            throw new RuntimeException("修改失败，您尚未对该电影发表长评");
        }
        CommentEvent event = new CommentEvent(userId, dto.getMovieId(), null, 2, "UPDATE", dto.getContent().length());
        kafkaEventPublisher.publishCommentEvent(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(String userId, Long commentId) {
        // 先查询当前状态
        CommentLike existing = commentLikeMapper.selectByCommentAndUser(commentId, userId);
        
        if (existing != null) {
            // 取消点赞：先删除点赞记录，再减少计数
            // 即使后续减少计数失败，删除记录后重新点赞会重新计数，数据最终一致
            commentLikeMapper.delete(commentId, userId);
            commentMapper.updateVotes(commentId, -1);
            CommentLikeEvent event = new CommentLikeEvent(userId, commentId, "UNLIKE");
            kafkaEventPublisher.publishCommentLikeEvent(event);
            return false;
        } else {
            // 点赞：先插入记录（利用唯一索引保证幂等性），再增加计数
            try {
                CommentLike like = new CommentLike();
                like.setCommentId(commentId);
                like.setUserId(userId);
                like.setCreateTime(new java.util.Date());
                commentLikeMapper.insert(like);
                commentMapper.updateVotes(commentId, 1);
                CommentLikeEvent event = new CommentLikeEvent(userId, commentId, "LIKE");
                kafkaEventPublisher.publishCommentLikeEvent(event);
                return true;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发情况下，另一个请求已经插入了记录
                // 由于事务会回滚，但此时 votes 可能已被其他事务修改
                // 返回当前实际状态（已点赞）
                return true;
            }
        }
    }

    @Override
    public boolean isLiked(String userId, Long commentId) {
        return commentLikeMapper.selectByCommentAndUser(commentId, userId) != null;
    }

    @Override
    public PageInfo<Comment> getUserComments(String userId, int page, int size) {
        PageHelper.startPage(page, size);
        try {
            List<Comment> list = commentMapper.selectByUserId(userId);
            return new PageInfo<>(list);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional
    public void deleteComment(String userId, Long commentId) {
        int rows = commentMapper.deleteByIdAndUserId(commentId, userId);
        if (rows == 0) {
            throw new RuntimeException("删除失败，评论不存在或您无权删除");
        }
        CommentEvent event = new CommentEvent(userId, null, commentId, null, "DELETE", null);
        kafkaEventPublisher.publishCommentEvent(event);
    }

    @Override
    public PageInfo<CommentVO> getCommentsByType(Long movieId, String currentUserId, Integer type, int page, int size) {
        PageHelper.startPage(page, size);
        try {
            List<CommentVO> list = commentMapper.selectWithRatingByMovieIdAndType(movieId, currentUserId, type);
            // 处理内容摘要
            for (CommentVO vo : list) {
                vo.processContentSummary();
            }
            return new PageInfo<>(list);
        } finally {
            PageHelper.clearPage();
        }
    }
}
