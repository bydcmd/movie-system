package com.movie.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.backend.dto.CommentVO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.User;
import com.movie.backend.exception.BusinessException;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.service.CommentService;
import com.movie.backend.service.TokenBlacklistService;
import com.movie.backend.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private String bearerTokenFor(String userId) {
        return "Bearer " + JwtUtil.generateAccessToken(userId, "TestUser", 1, 1);
    }

    private void mockAuthenticatedUser(String userId) {
        User user = new User();
        user.setId(userId);
        when(userMapper.selectById(userId)).thenReturn(user);
        when(tokenBlacklistService.isBlacklisted(any(String.class))).thenReturn(false);
    }

    @Test
    public void guestCannotViewCommentsBeyondFirst20() throws Exception {
        mockMvc.perform(get("/movies/1/comments")
                        .param("page", "3")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("游客最多查看前20条评论"));

        verifyNoInteractions(commentService);
    }

    @Test
    public void invalidCommentTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/movies/1/comments")
                        .param("type", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("type 只能为 1(短评) 或 2(长评)"));

        verifyNoInteractions(commentService);
    }

    @Test
    public void getMyMovieCommentReturnsShortComment() throws Exception {
        mockAuthenticatedUser("user123");

        Comment comment = new Comment();
        comment.setId(99L);
        comment.setMovieId(1L);
        comment.setUserId("user123");
        comment.setType(1);
        comment.setContent("短评内容");
        when(commentService.getUserShortComment("user123", 1L)).thenReturn(comment);

        mockMvc.perform(get("/movies/1/comments/me")
                        .header("Authorization", bearerTokenFor("user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.type").value(1))
                .andExpect(jsonPath("$.data.content").value("短评内容"));

        verify(commentService).getUserShortComment("user123", 1L);
    }

    @Test
    public void getMovieLongReviewDetailReturnsReview() throws Exception {
        CommentVO review = new CommentVO();
        review.setId(99L);
        review.setMovieId(1L);
        review.setType(2);
        review.setTitle("一篇长评");
        review.setContent("长评正文");
        review.setUserNickname("影迷甲");

        when(commentService.getMovieLongReviewDetail(1L, 99L, null)).thenReturn(review);

        mockMvc.perform(get("/movies/1/long-reviews/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.movieId").value(1))
                .andExpect(jsonPath("$.data.type").value(2))
                .andExpect(jsonPath("$.data.title").value("一篇长评"))
                .andExpect(jsonPath("$.data.userNickname").value("影迷甲"));

        verify(commentService).getMovieLongReviewDetail(1L, 99L, null);
    }

    @Test
    public void getMovieLongReviewDetailReturnsNotFoundWhenMissing() throws Exception {
        doThrow(new BusinessException(404, "长评不存在"))
                .when(commentService)
                .getMovieLongReviewDetail(1L, 999L, null);

        mockMvc.perform(get("/movies/1/long-reviews/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("长评不存在"));
    }

    @Test
    public void submitMovieCommentDuplicateReturnsConflict() throws Exception {
        mockAuthenticatedUser("user123");

        doThrow(new BusinessException(409, "您已经发表过短评了，无法重复发布"))
                .when(commentService)
                .submitComment(eq("user123"), eq(1L), eq("短评内容"));

        mockMvc.perform(post("/movies/1/comments")
                        .header("Authorization", bearerTokenFor("user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentPayload("短评内容"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("您已经发表过短评了，无法重复发布"));
    }

    private record CommentPayload(String content) {
    }
}
