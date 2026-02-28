package com.movie.backend.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.entity.User;
import com.movie.backend.service.AdminService;
import com.movie.backend.service.AnalyticsService;
import com.movie.backend.service.GenreService;
import com.movie.backend.service.RegionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

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

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

    @InjectMocks
    private AdminController adminController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AdminService adminService;

    @Mock
    private GenreService genreService;

    @Mock
    private RegionService regionService;

    @Mock
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    @Test
    public void testGetDashboardStats() throws Exception {
        Map<String, Object> mockStats = new HashMap<>();
        mockStats.put("userCount", 100);
        mockStats.put("movieCount", 500);
        mockStats.put("commentCount", 300);

        when(adminService.getDashboardStats()).thenReturn(mockStats);

        mockMvc.perform(get("/admin/dashboard/stats")
                        )
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

        mockMvc.perform(get("/admin/users")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].id").value("test_user"));
    }
    
    @Test
    public void testAddMovie() throws Exception {
        Movie movie = new Movie();
        movie.setName("New Movie");
        movie.setId(8888L);

        mockMvc.perform(post("/admin/movies")
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

        mockMvc.perform(get("/admin/people")
                        )
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

        mockMvc.perform(get("/admin/comments")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].content").value("Great Movie!"));
    }

}
