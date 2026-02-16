package com.movie.backend.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.service.AdminService;
import com.movie.backend.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 生成管理员测试用的JWT token
     */
    private String generateAdminToken() {
        return JwtUtil.generateAccessToken("admin_001", "AdminTest", 0, 1);
    }

    @BeforeEach
    public void setupUser() {
        User admin = new User();
        admin.setId("admin_001");
        admin.setRole(0);
        admin.setStatus(0);
        admin.setPasswordVersion(1);
        when(userMapper.selectById(anyString())).thenReturn(admin);
    }

    @Test
    public void testGetDashboardStats() throws Exception {
        Map<String, Object> mockStats = new HashMap<>();
        mockStats.put("userCount", 100);
        mockStats.put("movieCount", 500);
        mockStats.put("commentCount", 300);

        when(adminService.getDashboardStats()).thenReturn(mockStats);

        String token = generateAdminToken();

        mockMvc.perform(get("/admin/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userCount").value(100))
                .andExpect(jsonPath("$.data.commentCount").value(300));
    }

    @Test
    public void testGetUserList() throws Exception {
        User user = new User();
        user.setId("test_user");
        user.setNickname("Test Admin View");
        
        PageInfo<User> pageInfo = new PageInfo<>(Collections.singletonList(user));

        when(adminService.getUserList(anyString(), anyInt(), anyInt())).thenReturn(pageInfo);

        String token = generateAdminToken();

        mockMvc.perform(get("/admin/user/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].id").value("test_user"));
    }
    
    @Test
    public void testAddMovie() throws Exception {
        Movie movie = new Movie();
        movie.setName("New Movie");
        movie.setId(8888L);

        String token = generateAdminToken();

        mockMvc.perform(post("/admin/movie/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(movie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testGetPersonList() throws Exception {
        Person person = new Person();
        person.setName("Test Actor");
        PageInfo<Person> pageInfo = new PageInfo<>(Collections.singletonList(person));

        when(adminService.getPersonList(anyString(), anyInt(), anyInt())).thenReturn(pageInfo);

        String token = generateAdminToken();

        mockMvc.perform(get("/admin/person/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].name").value("Test Actor"));
    }

    @Test
    public void testGetCommentList() throws Exception {
        Comment comment = new Comment();
        comment.setContent("Great Movie!");
        PageInfo<Comment> pageInfo = new PageInfo<>(Collections.singletonList(comment));

        when(adminService.getCommentList(anyString(), anyInt(), anyInt())).thenReturn(pageInfo);

        String token = generateAdminToken();

        mockMvc.perform(get("/admin/comment/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].content").value("Great Movie!"));
    }
}
