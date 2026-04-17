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
    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_PUBLISHED = 2;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private CommentLikeMapper commentLikeMapper;

    @Autowired
    private RatingService ratingService;

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
            for (CommentVO vo : list) {
                enrichCommentContentSummary(vo);
            }
            return new PageInfo<>(list);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    public CommentVO getMovieLongReviewDetail(Long movieId, Long commentId, String currentUserId) {
        CommentVO review = commentMapper.selectLongReviewDetail(movieId, commentId, currentUserId);
        if (review == null) {
            throw new BusinessException(404, "长评不存在");
        }

        enrichCommentContentSummary(review);
        return review;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitComment(String userId, Long movieId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("短评内容不能超过500字");
        }

        try {
            Comment comment = new Comment();
            comment.setUserId(userId);
            comment.setMovieId(movieId);
            comment.setContent(content.trim());
            comment.setType(SHORT_COMMENT_TYPE);
            comment.setVotes(0);
            comment.setVersion(0);
            comment.setStatus(STATUS_PUBLISHED);
            comment.setCommentTime(new java.util.Date());

            commentMapper.insert(comment);
        } catch (org.springframework.dao.DuplicateKeyException e) {
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

        TiptapJsonValidator.ValidationResult validationResult = TiptapJsonValidator.validate(dto.getContent());
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
            comment.setStatus(STATUS_PUBLISHED);
            comment.setCommentTime(new java.util.Date());

            commentMapper.insert(comment);
            Comment existingDraft = commentMapper.selectByUserAndMovieAndTypeAndStatus(
                    userId, dto.getMovieId(), LONG_REVIEW_TYPE, STATUS_DRAFT);
            if (existingDraft != null) {
                commentMapper.deleteByIdAndUserId(existingDraft.getId(), userId);
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(409, "您已经发表过长评了，如需修改请前往个人中心");
        }
    }

    @Override
    public Comment getUserShortComment(String userId, Long movieId) {
        return commentMapper.selectByUserAndMovieAndType(userId, movieId, SHORT_COMMENT_TYPE);
    }

    @Override
    public Comment getUserLongReview(String userId, Long movieId) {
        Comment draft = commentMapper.selectByUserAndMovieAndTypeAndStatus(userId, movieId, LONG_REVIEW_TYPE, STATUS_DRAFT);
        if (draft != null) {
            return draft;
        }
        return commentMapper.selectByUserAndMovieAndTypeAndStatus(userId, movieId, LONG_REVIEW_TYPE, STATUS_PUBLISHED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCommentWithRating(String userId, Long movieId, String content, Integer rating) {
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

        ratingService.updateRating(userId, movieId, rating);

        int rows = commentMapper.updateByUserAndMovieAndType(userId, movieId, SHORT_COMMENT_TYPE, content.trim(), new java.util.Date());
        if (rows == 0) {
            throw new BusinessException(404, "修改失败，您尚未对该电影发表短评");
        }
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

        int rows = commentMapper.updateByUserAndMovieAndType(userId, movieId, SHORT_COMMENT_TYPE, content.trim(), new java.util.Date());
        if (rows == 0) {
            throw new BusinessException(404, "修改失败，您尚未对该电影发表短评");
        }
    }

    @Override
    @Transactional
    public void updateLongReview(String userId, Long movieId, String title, String content, Integer version) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("长评标题不能为空");
        }
        if (title.length() > 100) {
            throw new IllegalArgumentException("标题不能超过100字");
        }
        if (version == null) {
            throw new IllegalArgumentException("版本号不能为空");
        }

        TiptapJsonValidator.ValidationResult validationResult = TiptapJsonValidator.validate(content);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getMessage());
        }

        Comment comment = commentMapper.selectByUserAndMovieAndTypeAndStatus(userId, movieId, LONG_REVIEW_TYPE, STATUS_PUBLISHED);
        if (comment == null) {
            throw new BusinessException(404, "修改失败，您尚未对该电影发表长评");
        }

        int rows = commentMapper.updateLongComment(
                comment.getId(),
                userId,
                movieId,
                title.trim(),
                content.trim(),
                new java.util.Date(),
                version
        );
        if (rows == 0) {
            throw new BusinessException(409, "长评已被更新，请刷新后重试");
        }
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

            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return true;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlikeComment(String userId, Long commentId) {
        int deleted = commentLikeMapper.delete(commentId, userId);
        if (deleted <= 0) {
            return false;
        }

        int updated = commentMapper.updateVotes(commentId, -1);
        if (updated <= 0) {
            throw new BusinessException(404, "评论不存在");
        }

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
    @Transactional(rollbackFor = Exception.class)
    public int deleteComments(String userId, List<Long> commentIds) {
        int deletedCount = 0;
        for (Long commentId : commentIds) {
            int rows = commentMapper.deleteByIdAndUserId(commentId, userId);
            if (rows > 0) {
                commentLikeMapper.deleteByCommentId(commentId);
                deletedCount++;
            }
        }
        return deletedCount;
    }

    @Override
    public PageInfo<CommentVO> getCommentsByType(Long movieId, String currentUserId, Integer type, int page, int size) {
        PageHelper.startPage(page, size);
        try {
            List<CommentVO> list = commentMapper.selectWithRatingByMovieIdAndType(movieId, currentUserId, type);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLongReviewDraft(String userId, Long movieId, String title, String content) {
        if (StringUtils.hasText(title) && title.length() > 100) {
            throw new IllegalArgumentException("标题不能超过100字");
        }

        if (StringUtils.hasText(content)) {
            TiptapJsonValidator.ValidationResult validationResult = TiptapJsonValidator.validate(content);
            if (!validationResult.isValid()) {
                throw new IllegalArgumentException(validationResult.getMessage());
            }
        }

        Comment existingDraft = commentMapper.selectByUserAndMovieAndTypeAndStatus(userId, movieId, LONG_REVIEW_TYPE, STATUS_DRAFT);

        if (existingDraft != null) {
            int rows = commentMapper.updateDraftContent(
                    userId,
                    movieId,
                    LONG_REVIEW_TYPE,
                    StringUtils.hasText(title) ? title.trim() : null,
                    content != null ? content.trim() : null,
                    new java.util.Date(),
                    existingDraft.getVersion()
            );
            if (rows == 0) {
                throw new BusinessException(409, "草稿已被更新，请刷新后重试");
            }
            return;
        }

        Comment draft = new Comment();
        draft.setUserId(userId);
        draft.setMovieId(movieId);
        draft.setTitle(StringUtils.hasText(title) ? title.trim() : null);
        draft.setContent(content != null ? content.trim() : null);
        draft.setType(LONG_REVIEW_TYPE);
        draft.setVotes(0);
        draft.setVersion(0);
        draft.setStatus(STATUS_DRAFT);
        draft.setCommentTime(new java.util.Date());

        commentMapper.insert(draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLongReviewDraft(String userId, Long movieId, String title, String content, Integer version) {
        if (StringUtils.hasText(title) && title.length() > 100) {
            throw new IllegalArgumentException("标题不能超过100字");
        }
        if (version == null) {
            throw new IllegalArgumentException("版本号不能为空");
        }

        if (StringUtils.hasText(content)) {
            TiptapJsonValidator.ValidationResult validationResult = TiptapJsonValidator.validate(content);
            if (!validationResult.isValid()) {
                throw new IllegalArgumentException(validationResult.getMessage());
            }
        }

        Comment draft = commentMapper.selectByUserAndMovieAndTypeAndStatus(userId, movieId, LONG_REVIEW_TYPE, STATUS_DRAFT);
        if (draft == null) {
            throw new BusinessException(404, "草稿不存在");
        }

        int rows = commentMapper.updateDraftContent(
                userId,
                movieId,
                LONG_REVIEW_TYPE,
                StringUtils.hasText(title) ? title.trim() : null,
                content != null ? content.trim() : null,
                new java.util.Date(),
                version
        );

        if (rows == 0) {
            throw new BusinessException(409, "草稿已被更新，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishDraft(String userId, Long commentId) {
        Comment draft = commentMapper.selectByIdAndUserId(commentId, userId);

        if (draft == null) {
            throw new BusinessException(404, "草稿不存在或您无权操作");
        }

        if (draft.getType() != LONG_REVIEW_TYPE) {
            throw new BusinessException(400, "只能发布长评草稿");
        }
        if (draft.getStatus() != STATUS_DRAFT) {
            throw new BusinessException(400, "该评论已发布，无需重复操作");
        }

        if (!StringUtils.hasText(draft.getTitle())) {
            throw new IllegalArgumentException("发布前请填写长评标题");
        }
        if (!StringUtils.hasText(draft.getContent())) {
            throw new IllegalArgumentException("发布前请填写长评内容");
        }

        String normalizedTitle = draft.getTitle().trim();
        String normalizedContent = draft.getContent().trim();

        Comment existingPublished = commentMapper.selectByUserAndMovieAndTypeAndStatus(userId, draft.getMovieId(), LONG_REVIEW_TYPE, STATUS_PUBLISHED);
        if (existingPublished != null) {
            int updateRows = commentMapper.updateLongComment(
                    existingPublished.getId(),
                    userId,
                    draft.getMovieId(),
                    normalizedTitle,
                    normalizedContent,
                    new java.util.Date(),
                    existingPublished.getVersion()
            );
            if (updateRows == 0) {
                throw new BusinessException(409, "正式长评已被更新，请刷新后重试");
            }

            int deleteRows = commentMapper.deleteByIdAndUserId(commentId, userId);
            if (deleteRows == 0) {
                throw new BusinessException(500, "发布失败");
            }
            return;
        }

        int rows = commentMapper.updateStatus(commentId, userId, STATUS_PUBLISHED, new java.util.Date(), draft.getVersion());
        if (rows == 0) {
            throw new BusinessException(409, "草稿已被更新，请刷新后重试");
        }
    }
}
