package com.movie.backend.service.impl;

import com.movie.backend.common.UserStatus;
import com.movie.backend.dto.LoginDTO;
import com.movie.backend.dto.PublicUserVO;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.mapper.WatchedMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private WatchedMapper watchedMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    public void getPublicUserInfoWithStatsReturnsNullForCancelledUser() {
        User user = new User();
        user.setId("cancelled_user");
        user.setStatus(UserStatus.CANCELLED);
        user.setNickname("已注销用户");

        when(userMapper.selectById("cancelled_user")).thenReturn(user);

        PublicUserVO result = userService.getPublicUserInfoWithStats("cancelled_user");

        assertNull(result);
        verifyNoInteractions(commentMapper, watchedMapper);
    }

    @Test
    public void loginRejectsCancelledUser() {
        User user = new User();
        user.setId("cancelled_user");
        user.setStatus(UserStatus.CANCELLED);

        when(userMapper.selectById("cancelled_user")).thenReturn(user);

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setId("cancelled_user");
        loginDTO.setPassword("irrelevant");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> userService.login(loginDTO));

        assertEquals("该账号已注销，无法登录", exception.getMessage());
    }
}
