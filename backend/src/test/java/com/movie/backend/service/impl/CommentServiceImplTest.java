package com.movie.backend.service.impl;

import com.movie.backend.dto.LongReviewDTO;
import com.movie.backend.entity.Comment;
import com.movie.backend.exception.BusinessException;
import com.movie.backend.mapper.CommentLikeMapper;
import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.service.RatingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CommentServiceImplTest {

    private static final String VALID_LONG_REVIEW_CONTENT =
            "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"长评正文\"}]}]}";

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private CommentLikeMapper commentLikeMapper;

    @Mock
    private RatingService ratingService;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    public void getUserShortCommentQueriesShortCommentType() {
        Comment expected = new Comment();
        expected.setId(10L);

        when(commentMapper.selectByUserAndMovieAndType("user123", 1L, 1)).thenReturn(expected);

        Comment actual = commentService.getUserShortComment("user123", 1L);

        assertSame(expected, actual);
        verify(commentMapper).selectByUserAndMovieAndType("user123", 1L, 1);
    }

    @Test
    public void updateCommentOnlyUpdatesShortComment() {
        when(commentMapper.updateByUserAndMovieAndType(eq("user123"), eq(1L), eq(1), eq("短评"), any()))
                .thenReturn(1);

        commentService.updateComment("user123", 1L, "  短评  ");

        verify(commentMapper).updateByUserAndMovieAndType(eq("user123"), eq(1L), eq(1), eq("短评"), any());
        verify(commentMapper, never()).updateLongComment(anyLong(), anyString(), anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    public void updateLongReviewUsesVersionedMapper() {
        Comment published = new Comment();
        published.setId(31L);

        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 2)).thenReturn(published);
        when(commentMapper.updateLongComment(eq(31L), eq("user123"), eq(1L), eq("长评标题"), eq(VALID_LONG_REVIEW_CONTENT), any(), eq(3)))
                .thenReturn(1);

        commentService.updateLongReview("user123", 1L, "  长评标题  ", VALID_LONG_REVIEW_CONTENT, 3);

        verify(commentMapper).updateLongComment(eq(31L), eq("user123"), eq(1L), eq("长评标题"), eq(VALID_LONG_REVIEW_CONTENT), any(), eq(3));
    }

    @Test
    public void updateLongReviewThrowsConflictWhenVersionMismatch() {
        Comment published = new Comment();
        published.setId(32L);

        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 2)).thenReturn(published);
        when(commentMapper.updateLongComment(eq(32L), eq("user123"), eq(1L), eq("长评标题"), eq(VALID_LONG_REVIEW_CONTENT), any(), eq(4)))
                .thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> commentService.updateLongReview("user123", 1L, "长评标题", VALID_LONG_REVIEW_CONTENT, 4)
        );

        assertEquals(409, exception.getCode());
        assertEquals("长评已被更新，请刷新后重试", exception.getMessage());
    }

    @Test
    public void getUserLongReviewPrefersDraftWhenDraftExists() {
        Comment draft = new Comment();
        draft.setId(21L);
        draft.setStatus(1);

        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 1)).thenReturn(draft);

        Comment actual = commentService.getUserLongReview("user123", 1L);

        assertSame(draft, actual);
        verify(commentMapper).selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 1);
        verify(commentMapper, never()).selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 2);
    }

    @Test
    public void getUserLongReviewFallsBackToPublishedWhenDraftMissing() {
        Comment published = new Comment();
        published.setId(22L);
        published.setStatus(2);

        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 1)).thenReturn(null);
        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 2)).thenReturn(published);

        Comment actual = commentService.getUserLongReview("user123", 1L);

        assertSame(published, actual);
        verify(commentMapper).selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 1);
        verify(commentMapper).selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 2);
    }

    @Test
    public void submitLongReviewDeletesExistingDraftAfterCreate() {
        LongReviewDTO dto = new LongReviewDTO();
        dto.setMovieId(1L);
        dto.setTitle("长评标题");
        dto.setContent(VALID_LONG_REVIEW_CONTENT);

        Comment draft = new Comment();
        draft.setId(66L);

        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 1)).thenReturn(draft);

        commentService.submitLongReview("user123", dto);

        verify(commentMapper).insert(any(Comment.class));
        verify(commentMapper).selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 1);
        verify(commentMapper).deleteByIdAndUserId(66L, "user123");
    }

    @Test
    public void updateLongReviewDraftThrowsConflictWhenVersionMismatch() {
        Comment draft = new Comment();
        draft.setId(41L);
        draft.setStatus(1);

        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 1)).thenReturn(draft);
        when(commentMapper.updateDraftContent(eq("user123"), eq(1L), eq(2), eq("草稿标题"), eq(VALID_LONG_REVIEW_CONTENT), any(), eq(5)))
                .thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> commentService.updateLongReviewDraft("user123", 1L, "草稿标题", VALID_LONG_REVIEW_CONTENT, 5)
        );

        assertEquals(409, exception.getCode());
        assertEquals("草稿已被更新，请刷新后重试", exception.getMessage());
    }

    @Test
    public void publishDraftUsesPublishedCommentVersionWhenMerging() {
        Comment draft = new Comment();
        draft.setId(90L);
        draft.setType(2);
        draft.setStatus(1);
        draft.setMovieId(1L);
        draft.setTitle("草稿标题");
        draft.setContent(VALID_LONG_REVIEW_CONTENT);

        Comment published = new Comment();
        published.setId(91L);
        published.setVersion(7);

        when(commentMapper.selectByIdAndUserId(90L, "user123")).thenReturn(draft);
        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 2)).thenReturn(published);
        when(commentMapper.updateLongComment(eq(91L), eq("user123"), eq(1L), eq("草稿标题"), eq(VALID_LONG_REVIEW_CONTENT), any(), eq(7)))
                .thenReturn(1);
        when(commentMapper.deleteByIdAndUserId(90L, "user123")).thenReturn(1);

        commentService.publishDraft("user123", 90L);

        verify(commentMapper).updateLongComment(eq(91L), eq("user123"), eq(1L), eq("草稿标题"), eq(VALID_LONG_REVIEW_CONTENT), any(), eq(7));
    }

    @Test
    public void publishDraftUsesDraftVersionWhenPublishingDirectly() {
        Comment draft = new Comment();
        draft.setId(92L);
        draft.setType(2);
        draft.setStatus(1);
        draft.setMovieId(1L);
        draft.setTitle("草稿标题");
        draft.setContent(VALID_LONG_REVIEW_CONTENT);
        draft.setVersion(8);

        when(commentMapper.selectByIdAndUserId(92L, "user123")).thenReturn(draft);
        when(commentMapper.selectByUserAndMovieAndTypeAndStatus("user123", 1L, 2, 2)).thenReturn(null);
        when(commentMapper.updateStatus(eq(92L), eq("user123"), eq(2), any(), eq(8))).thenReturn(1);

        commentService.publishDraft("user123", 92L);

        verify(commentMapper).updateStatus(eq(92L), eq("user123"), eq(2), any(), eq(8));
    }

    @Test
    public void submitCommentDuplicateThrowsConflict() {
        when(commentMapper.insert(any(Comment.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> commentService.submitComment("user123", 1L, "重复短评")
        );

        assertEquals(409, exception.getCode());
        assertEquals("您已经发表过短评了，无法重复发布", exception.getMessage());
    }

    @Test
    public void deleteCommentDeletesLikesAfterCommentDeletion() {
        when(commentMapper.deleteByIdAndUserId(88L, "user123")).thenReturn(1);

        assertEquals(1, commentService.deleteComments("user123", List.of(88L)));

        InOrder inOrder = inOrder(commentMapper, commentLikeMapper);
        inOrder.verify(commentMapper).deleteByIdAndUserId(88L, "user123");
        inOrder.verify(commentLikeMapper).deleteByCommentId(88L);
    }
}
