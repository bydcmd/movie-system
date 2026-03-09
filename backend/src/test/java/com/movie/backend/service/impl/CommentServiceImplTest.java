package com.movie.backend.service.impl;

import com.movie.backend.entity.Comment;
import com.movie.backend.exception.BusinessException;
import com.movie.backend.mapper.CommentLikeMapper;
import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.messaging.kafka.KafkaEventPublisher;
import com.movie.backend.service.RatingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

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

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private CommentLikeMapper commentLikeMapper;

    @Mock
    private RatingService ratingService;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

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
        verify(commentMapper, never()).updateLongComment(anyString(), anyLong(), anyString(), anyString(), any());
        verify(kafkaEventPublisher).publishCommentEvent(any());
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
        verify(kafkaEventPublisher, never()).publishCommentEvent(any());
    }

    @Test
    public void deleteCommentDeletesLikesAfterCommentDeletion() {
        when(commentMapper.deleteByIdAndUserId(88L, "user123")).thenReturn(1);

        commentService.deleteComment("user123", 88L);

        InOrder inOrder = inOrder(commentMapper, commentLikeMapper, kafkaEventPublisher);
        inOrder.verify(commentMapper).deleteByIdAndUserId(88L, "user123");
        inOrder.verify(commentLikeMapper).deleteByCommentId(88L);
        inOrder.verify(kafkaEventPublisher).publishCommentEvent(any());
    }
}
