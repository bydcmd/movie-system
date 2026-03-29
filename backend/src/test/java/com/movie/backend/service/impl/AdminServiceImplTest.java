package com.movie.backend.service.impl;

import com.github.pagehelper.PageInfo;
import com.movie.backend.common.UserStatus;
import com.movie.backend.dto.AdminDashboardOverviewVO;
import com.movie.backend.dto.AdminDashboardVO;
import com.movie.backend.dto.AdminTrendPointVO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.AdminDashboardMapper;
import com.movie.backend.mapper.CommentLikeMapper;
import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.mapper.PersonMapper;
import com.movie.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AdminServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private MovieMapper movieMapper;

    @Mock
    private PersonMapper personMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private CommentLikeMapper commentLikeMapper;

    @Mock
    private AdminDashboardMapper adminDashboardMapper;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    public void getDashboardStatsReturnsCounts() {
        AdminDashboardOverviewVO overview = new AdminDashboardOverviewVO();
        overview.setTotalUsers(10);
        overview.setTotalMovies(20);
        overview.setPublishedCommentCount(30);
        overview.setDraftCommentCount(5);

        List<AdminTrendPointVO> trend = List.of(buildTrendPoint("2026-03-19", 1));

        when(adminDashboardMapper.selectOverview()).thenReturn(overview);
        when(adminDashboardMapper.selectUserRegistrationTrend()).thenReturn(trend);
        when(adminDashboardMapper.selectPublishedCommentTrend()).thenReturn(trend);
        when(adminDashboardMapper.selectFavoriteTrend()).thenReturn(trend);
        when(adminDashboardMapper.selectRatingTrend()).thenReturn(trend);
        when(adminDashboardMapper.selectViewTrend()).thenReturn(trend);
        when(adminDashboardMapper.selectWatchedTrend()).thenReturn(trend);

        AdminDashboardVO stats = adminService.getDashboardStats();

        assertNotNull(stats);
        assertEquals(10, stats.getUserCount());
        assertEquals(20, stats.getMovieCount());
        assertEquals(30, stats.getCommentCount());
        assertEquals(5, stats.getOverview().getDraftCommentCount());
        assertEquals("2026-03-19", stats.getTrends().getUserRegistrations().get(0).getDate());
    }

    @Test
    public void getUserListPassesStatusFilterToMapper() {
        User user = new User();
        user.setId("frozen_user");
        user.setStatus(UserStatus.FROZEN);

        when(userMapper.selectList("frozen", UserStatus.FROZEN)).thenReturn(List.of(user));

        PageInfo<User> page = adminService.getUserList("frozen", UserStatus.FROZEN, 1, 10);

        verify(userMapper).selectList("frozen", UserStatus.FROZEN);
        assertEquals(1, page.getList().size());
        assertEquals("frozen_user", page.getList().get(0).getId());
    }

    @Test
    public void freezeUserMarksUserFrozenAndInvalidatesTokens() {
        User existing = new User();
        existing.setId("frozen_user");
        existing.setStatus(0);
        existing.setPasswordVersion(3);

        when(userMapper.selectById("frozen_user")).thenReturn(existing);
        when(userMapper.update(any(User.class))).thenReturn(1);

        adminService.freezeUser("frozen_user");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User updated = captor.getValue();

        assertEquals("frozen_user", updated.getId());
        assertEquals(UserStatus.FROZEN, updated.getStatus());
        assertEquals(4, updated.getPasswordVersion());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    public void unfreezeUserRestoresActiveStatusWithoutChangingPasswordVersion() {
        User existing = new User();
        existing.setId("frozen_user");
        existing.setStatus(1);
        existing.setPasswordVersion(4);

        when(userMapper.selectById("frozen_user")).thenReturn(existing);
        when(userMapper.update(any(User.class))).thenReturn(1);

        adminService.unfreezeUser("frozen_user");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User updated = captor.getValue();

        assertEquals("frozen_user", updated.getId());
        assertEquals(UserStatus.ACTIVE, updated.getStatus());
        assertNull(updated.getPasswordVersion());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    public void deleteUserMarksUserCancelledAndInvalidatesTokens() {
        User existing = new User();
        existing.setId("cancelled_user");
        existing.setStatus(UserStatus.ACTIVE);
        existing.setPasswordVersion(5);

        when(userMapper.selectById("cancelled_user")).thenReturn(existing);
        when(userMapper.update(any(User.class))).thenReturn(1);

        adminService.deleteUser("cancelled_user");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User updated = captor.getValue();

        assertEquals("cancelled_user", updated.getId());
        assertEquals(UserStatus.CANCELLED, updated.getStatus());
        assertEquals("已注销用户", updated.getNickname());
        assertEquals(6, updated.getPasswordVersion());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    public void hideCommentMarksCommentHidden() {
        Comment existing = new Comment();
        existing.setId(99L);
        existing.setStatus(2);

        when(commentMapper.selectById(99L)).thenReturn(existing);
        when(commentMapper.updateStatusById(99L, 3)).thenReturn(1);

        adminService.hideComment(99L);

        verify(commentMapper).updateStatusById(99L, 3);
    }

    @Test
    public void restoreCommentMarksCommentPublished() {
        Comment existing = new Comment();
        existing.setId(100L);
        existing.setStatus(3);

        when(commentMapper.selectById(100L)).thenReturn(existing);
        when(commentMapper.updateStatusById(100L, 2)).thenReturn(1);

        adminService.restoreComment(100L);

        verify(commentMapper).updateStatusById(100L, 2);
    }

    private AdminTrendPointVO buildTrendPoint(String date, int value) {
        AdminTrendPointVO point = new AdminTrendPointVO();
        point.setDate(date);
        point.setValue(value);
        return point;
    }
}
