package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.CommentVO;
import com.movie.backend.dto.LongReviewDTO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.CommentLike;
import com.movie.backend.exception.BusinessException;
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

    private static final int SHORT_COMMENT_TYPE = 1;
    private static final int LONG_REVIEW_TYPE = 2;

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
                enrichCommentContentSummary(vo);
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
            comment.setType(SHORT_COMMENT_TYPE);
            comment.setVotes(0);
            comment.setVersion(0); // 初始版本号
            comment.setCommentTime(new java.util.Date());

            commentMapper.insert(comment);
            // 插入后 comment.getId() 会自动填充数据库生成的自增ID
            CommentEvent event = new CommentEvent(userId, movieId, comment.getId(), SHORT_COMMENT_TYPE, "CREATE", content.length());
            kafkaEventPublisher.publishCommentEvent(event);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引冲突，说明用户已发表过短评
            throw new BusinessException(409, "您已经发表过短评了，无法重复发布");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitLongReview(String userId, LongReviewDTO dto) {
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new IllegalArgumentException("长评标题不能为空");
        }
        if (dto.getTitle().length() > 100) {
            throw new IllegalArgumentException("标题不能超过100字");
        }

        TiptapJsonValidator.ValidationResult validationResult =
                TiptapJsonValidator.validate(dto.getContent());
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getMessage());
        }

        try {
            Comment comment = new Comment();
            comment.setUserId(userId);
            comment.setMovieId(dto.getMovieId());
            comment.setTitle(dto.getTitle().trim());
            comment.setContent(dto.getContent().trim());
            comment.setType(LONG_REVIEW_TYPE);
            comment.setVotes(0);
            comment.setVersion(0);
            comment.setCommentTime(new java.util.Date());

            commentMapper.insert(comment);
            CommentEvent event = new CommentEvent(userId, dto.getMovieId(), comment.getId(), LONG_REVIEW_TYPE, "CREATE", dto.getContent().length());
            kafkaEventPublisher.publishCommentEvent(event);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(409, "您已经发表过长评了，如需修改请前往个人中心");
        }
    }

    @Override
    public Comment getUserShortComment(String userId, Long movieId) {
        return commentMapper.selectByUserAndMovieAndType(userId, movieId, SHORT_COMMENT_TYPE);
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
        int rows = commentMapper.updateByUserAndMovieAndType(
                userId,
                movieId,
                SHORT_COMMENT_TYPE,
                content.trim(),
                new java.util.Date()
        );
        if (rows == 0) {
            throw new BusinessException(404, "修改失败，您尚未对该电影发表短评");
        }
        CommentEvent event = new CommentEvent(userId, movieId, null, SHORT_COMMENT_TYPE, "UPDATE", content.length());
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

        int rows = commentMapper.updateByUserAndMovieAndType(
                userId,
                movieId,
                SHORT_COMMENT_TYPE,
                content.trim(),
                new java.util.Date()
        );
        if (rows == 0) {
            throw new BusinessException(404, "修改失败，您尚未对该电影发表短评");
        }
        CommentEvent event = new CommentEvent(userId, movieId, null, SHORT_COMMENT_TYPE, "UPDATE", content.length());
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

        TiptapJsonValidator.ValidationResult validationResult =
                TiptapJsonValidator.validate(content);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getMessage());
        }

        int rows = commentMapper.updateLongComment(
                userId,
                movieId,
                title.trim(),
                content.trim(),
                new java.util.Date()
        );
        if (rows == 0) {
            throw new BusinessException(404, "修改失败，您尚未对该电影发表长评");
        }
        CommentEvent event = new CommentEvent(userId, movieId, null, LONG_REVIEW_TYPE, "UPDATE", content.length());
        kafkaEventPublisher.publishCommentEvent(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean likeComment(String userId, Long commentId) {
        try {
            CommentLike like = new CommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            like.setCreateTime(new java.util.Date());
            commentLikeMapper.insert(like);

            int updated = commentMapper.updateVotes(commentId, 1);
            if (updated <= 0) {
                throw new BusinessException(404, "评论不存在");
            }

            CommentLikeEvent event = new CommentLikeEvent(userId, commentId, "LIKE");
            kafkaEventPublisher.publishCommentLikeEvent(event);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发或重复点赞下保持幂等：已点赞
            return true;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlikeComment(String userId, Long commentId) {
        int deleted = commentLikeMapper.delete(commentId, userId);
        if (deleted <= 0) {
            // 并发或重复取消点赞下保持幂等：未点赞
            return false;
        }

        int updated = commentMapper.updateVotes(commentId, -1);
        if (updated <= 0) {
            throw new BusinessException(404, "评论不存在");
        }

        CommentLikeEvent event = new CommentLikeEvent(userId, commentId, "UNLIKE");
        kafkaEventPublisher.publishCommentLikeEvent(event);
        return false;
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
            throw new BusinessException(404, "删除失败，评论不存在或您无权删除");
        }
        commentLikeMapper.deleteByCommentId(commentId);
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
                enrichCommentContentSummary(vo);
            }
            return new PageInfo<>(list);
        } finally {
            PageHelper.clearPage();
        }
    }

    private void enrichCommentContentSummary(CommentVO vo) {
        if (vo == null || vo.getContent() == null) {
            if (vo != null) {
                vo.setContentSummary(null);
                vo.setContentLength(0);
                vo.setIsJsonContent(false);
            }
            return;
        }

        String content = vo.getContent().trim();
        if (content.startsWith("{") && content.contains("\"type\":\"doc\"")) {
            vo.setIsJsonContent(true);
            String plainText = TiptapJsonValidator.extractPlainText(content);
            vo.setContentLength(plainText.length());
            vo.setContentSummary(TiptapJsonValidator.getSummary(content, 200));
            return;
        }

        vo.setIsJsonContent(false);
        vo.setContentLength(content.length());
        if (vo.getType() != null && vo.getType() == 2) {
            vo.setContentSummary(TiptapJsonValidator.summarizeText(content, 200));
        } else {
            vo.setContentSummary(content);
        }
    }
}
