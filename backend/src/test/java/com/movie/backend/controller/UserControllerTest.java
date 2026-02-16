package com.movie.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.backend.dto.LoginDTO;
import com.movie.backend.dto.RegisterDTO;
import com.movie.backend.dto.UpdateUserProfileDTO;
import com.movie.backend.dto.UserProfileVO;
import com.movie.backend.dto.UserVO;
import com.movie.backend.context.UserContext;
import com.movie.backend.entity.User;
import com.movie.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService; // Mock Service to avoid DB dependency in this test

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    public void tearDown() {
        UserContext.clear();
    }

    @Test
    public void testRegister_Success() throws Exception {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setId("user123");
        registerDTO.setPassword("123456");
        registerDTO.setNickname("TestUser");
        registerDTO.setEmail("test@test.com");

        mockMvc.perform(post("/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Success"));
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

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    public void testGetMyProfile_Success() throws Exception {
        User currentUser = new User();
        currentUser.setId("user123");
        UserContext.setCurrentUser(currentUser);

        UserProfileVO profileVO = new UserProfileVO();
        profileVO.setId("user123");
        profileVO.setNickname("TestUser");
        profileVO.setEmail("test@test.com");

        when(userService.getMyProfile("user123")).thenReturn(profileVO);

        mockMvc.perform(get("/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("user123"))
                .andExpect(jsonPath("$.data.nickname").value("TestUser"));
    }

    @Test
    public void testUpdateMyProfile_Success() throws Exception {
        User currentUser = new User();
        currentUser.setId("user123");
        UserContext.setCurrentUser(currentUser);

        UpdateUserProfileDTO dto = new UpdateUserProfileDTO();
        dto.setNickname("NewName");
        dto.setEmail("new@test.com");

        doNothing().when(userService).updateMyProfile(eq("user123"), any(UpdateUserProfileDTO.class));

        mockMvc.perform(put("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Success"));
    }

    @Test
    public void testUpdateMyProfile_InvalidEmail() throws Exception {
        User currentUser = new User();
        currentUser.setId("user123");
        UserContext.setCurrentUser(currentUser);

        UpdateUserProfileDTO dto = new UpdateUserProfileDTO();
        dto.setEmail("invalid-email");

        mockMvc.perform(put("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));
    }
}
