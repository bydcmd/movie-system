package com.movie.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MovieSearchDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.service.AnalyticsService;
import com.movie.backend.service.MovieService;
import com.movie.backend.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class MovieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MovieService movieService;

    @MockBean
    private AnalyticsService analyticsService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 生成测试用的JWT token
     */
    private String generateTestToken() {
        return JwtUtil.generateAccessToken("test_user_001", "TestUser", 1, 1);
    }

    @Test
    public void testGetDetail() throws Exception {
        Movie mockMovie = new Movie();
        mockMovie.setId(1294377L);
        mockMovie.setName("Inception");
        mockMovie.setScore(9.5);
        mockMovie.setYear(2010);

        when(movieService.getDetail(1294377L)).thenReturn(mockMovie);

        String token = generateTestToken();

        mockMvc.perform(get("/movie/detail/1294377")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Inception"))
                .andExpect(jsonPath("$.data.score").value(9.5));
    }

    @Test
    public void testSearch() throws Exception {
        MovieSearchDTO searchDTO = new MovieSearchDTO();
        searchDTO.setKeyword("Inception");
        searchDTO.setPage(1);
        searchDTO.setSize(10);

        Movie movie = new Movie();
        movie.setName("Inception");
        PageInfo<Movie> pageInfo = new PageInfo<>(Collections.singletonList(movie));

        when(movieService.search(any(MovieSearchDTO.class))).thenReturn(pageInfo);

        String token = generateTestToken();

        mockMvc.perform(post("/movie/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].name").value("Inception"));
    }
}
