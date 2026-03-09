package com.movie.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.backend.dto.LoginDTO;
import com.movie.backend.dto.RegisterDTO;
import com.movie.backend.dto.UpdateUserProfileDTO;
import com.movie.backend.dto.UserProfileVO;
import com.movie.backend.dto.UserVO;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.service.UserService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService; // Mock Service to avoid DB dependency in this test

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearerTokenFor(String userId) {
        return "Bearer " + JwtUtil.generateAccessToken(userId, "TestUser", 1, 1);
    }

    @Test
    public void testRegister_Success() throws Exception {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setId("user123");
        registerDTO.setPassword("123456");
        registerDTO.setNickname("TestUser");
        registerDTO.setEmail("test@test.com");

        mockMvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data").value("注册成功"));
    }

    @Test
    public void testLogin_Success() throws Exception {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setId("user123");
        loginDTO.setPassword("123456");

        UserVO mockUser = new UserVO();
        mockUser.setId("user123");
        mockUser.setNickname("TestUser");
        mockUser.setAccessToken("mock-jwt-access-token");
        mockUser.setRefreshToken("mock-jwt-refresh-token");

        when(userService.login(any(LoginDTO.class))).thenReturn(mockUser);

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")));
    }

    @Test
    public void testGetMyProfile_Success() throws Exception {
        User currentUser = new User();
        currentUser.setId("user123");
        when(userMapper.selectById("user123")).thenReturn(currentUser);
        when(tokenBlacklistService.isBlacklisted(any(String.class))).thenReturn(false);

        UserProfileVO profileVO = new UserProfileVO();
        profileVO.setId("user123");
        profileVO.setNickname("TestUser");
        profileVO.setEmail("test@test.com");

        when(userService.getMyProfile("user123")).thenReturn(profileVO);

        mockMvc.perform(get("/users/me/profile")
                        .header("Authorization", bearerTokenFor("user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("user123"))
                .andExpect(jsonPath("$.data.nickname").value("TestUser"));
    }

    @Test
    public void testUpdateMyProfile_Success() throws Exception {
        User currentUser = new User();
        currentUser.setId("user123");
        when(userMapper.selectById("user123")).thenReturn(currentUser);
        when(tokenBlacklistService.isBlacklisted(any(String.class))).thenReturn(false);

        UpdateUserProfileDTO dto = new UpdateUserProfileDTO();
        dto.setNickname("NewName");
        dto.setEmail("new@test.com");

        doNothing().when(userService).updateMyProfile(eq("user123"), any(UpdateUserProfileDTO.class));

        mockMvc.perform(patch("/users/me/profile")
                        .header("Authorization", bearerTokenFor("user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data").value("个人资料更新成功"));
    }

    @Test
    public void testUpdateMyProfile_InvalidEmail() throws Exception {
        User currentUser = new User();
        currentUser.setId("user123");
        when(userMapper.selectById("user123")).thenReturn(currentUser);
        when(tokenBlacklistService.isBlacklisted(any(String.class))).thenReturn(false);

        UpdateUserProfileDTO dto = new UpdateUserProfileDTO();
        dto.setEmail("invalid-email");

        mockMvc.perform(patch("/users/me/profile")
                        .header("Authorization", bearerTokenFor("user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));
    }
}
