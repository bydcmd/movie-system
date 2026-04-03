package com.movie.backend.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.AdminMovieDTO;
import com.movie.backend.dto.AdminDashboardOverviewVO;
import com.movie.backend.dto.AdminDashboardTrendsVO;
import com.movie.backend.dto.AdminDashboardVO;
import com.movie.backend.dto.AdminTrendPointVO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.entity.User;
import com.movie.backend.service.AdminService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class AdminControllersTest {

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    @InjectMocks
    private AdminUserController adminUserController;

    @InjectMocks
    private AdminMovieController adminMovieController;

    @InjectMocks
    private AdminPersonController adminPersonController;

    @InjectMocks
    private AdminCommentController adminCommentController;

    @InjectMocks
    private AdminGenreController adminGenreController;

    @InjectMocks
    private AdminRegionController adminRegionController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AdminService adminService;

    @Mock
    private GenreService genreService;

    @Mock
    private RegionService regionService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                adminDashboardController,
                adminUserController,
                adminMovieController,
                adminPersonController,
                adminCommentController,
                adminGenreController,
                adminRegionController
        ).build();
    }

    @Test
    public void testGetDashboardStats() throws Exception {
        AdminDashboardOverviewVO overview = new AdminDashboardOverviewVO();
        overview.setTotalUsers(100);
        overview.setTotalMovies(500);
        overview.setPublishedCommentCount(300);
        overview.setDraftCommentCount(20);

        AdminTrendPointVO point = new AdminTrendPointVO();
        point.setDate("2026-03-25");
        point.setValue(8);

        AdminDashboardTrendsVO trends = new AdminDashboardTrendsVO();
        trends.setUserRegistrations(List.of(point));
        trends.setPublishedComments(List.of(point));
        trends.setFavorites(List.of(point));
        trends.setRatings(List.of(point));
        trends.setViews(List.of(point));
        trends.setWatchedMovies(List.of(point));

        AdminDashboardVO mockStats = new AdminDashboardVO();
        mockStats.setUserCount(100);
        mockStats.setMovieCount(500);
        mockStats.setCommentCount(300);
        mockStats.setOverview(overview);
        mockStats.setTrends(trends);

        when(adminService.getDashboardStats()).thenReturn(mockStats);

        mockMvc.perform(get("/admin/dashboard/stats")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userCount").value(100))
                .andExpect(jsonPath("$.data.commentCount").value(300))
                .andExpect(jsonPath("$.data.overview.draftCommentCount").value(20))
                .andExpect(jsonPath("$.data.trends.userRegistrations[0].date").value("2026-03-25"));
    }

    @Test
    public void testGetUserList() throws Exception {
        User user = new User();
        user.setId("test_user");
        user.setNickname("Test Admin View");
        
        PageInfo<User> pageInfo = new PageInfo<>(Collections.singletonList(user));

        when(adminService.getUserList(anyString(), isNull(), anyInt(), anyInt())).thenReturn(pageInfo);

        mockMvc.perform(get("/admin/users")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].id").value("test_user"));
    }

    @Test
    public void testGetUserListWithStatusFilter() throws Exception {
        User user = new User();
        user.setId("frozen_user");
        user.setStatus(1);

        PageInfo<User> pageInfo = new PageInfo<>(Collections.singletonList(user));

        when(adminService.getUserList(anyString(), eq(1), anyInt(), anyInt())).thenReturn(pageInfo);

        mockMvc.perform(get("/admin/users")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].id").value("frozen_user"));

        verify(adminService).getUserList("", 1, 1, 10);
    }

    @Test
    public void testFreezeUser() throws Exception {
        mockMvc.perform(patch("/admin/users/test_user/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("用户已冻结"));
    }

    @Test
    public void testUnfreezeUser() throws Exception {
        mockMvc.perform(patch("/admin/users/test_user/unfreeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("用户已解冻"));
    }
    
    @Test
    public void testAddMovie() throws Exception {
        AdminMovieDTO movie = new AdminMovieDTO();
        movie.setName("New Movie");

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

    @Test
    public void testHideComment() throws Exception {
        mockMvc.perform(patch("/admin/comments/100/hide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("评论已隐藏"));
    }

    @Test
    public void testRestoreComment() throws Exception {
        mockMvc.perform(patch("/admin/comments/100/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("评论已恢复"));
    }

}
