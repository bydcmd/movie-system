package com.movie.backend.service.impl;

import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private MovieMapper movieMapper;

    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    public void getDashboardStatsReturnsCounts() {
        when(userMapper.countActiveUsers()).thenReturn(10);
        when(movieMapper.countAll()).thenReturn(20);
        when(commentMapper.countAll()).thenReturn(30);

        Map<String, Object> stats = adminService.getDashboardStats();

        assertNotNull(stats);
        assertEquals(10, stats.get("userCount"));
        assertEquals(20, stats.get("movieCount"));
        assertEquals(30, stats.get("commentCount"));
    }
}
