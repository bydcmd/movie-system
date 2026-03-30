package com.movie.backend.controller;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("分析接口测试")
class AnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("趋势榜支持 TOTAL 周期")
    void getTrendingMovies_SupportsTotalPeriod() throws Exception {
        TrendingMovieDTO dto = new TrendingMovieDTO();
        dto.setMovieId(1L);
        dto.setName("星际穿越");
        dto.setHotScore(98.6);
        dto.setCalcDate("2026-03-28");
        dto.setPeriod("TOTAL");
        dto.setRank(1);

        when(analyticsService.getTrendingMovies(TrendPeriod.TOTAL, 5)).thenReturn(List.of(dto));

        mockMvc.perform(get("/analytics/trending")
                        .param("period", "TOTAL")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].movieId").value(1))
                .andExpect(jsonPath("$.data[0].period").value("TOTAL"))
                .andExpect(jsonPath("$.data[0].rank").value(1));
    }

    @Test
    @DisplayName("兼容相似电影接口支持 ALS 相似类型")
    void getSimilarMovies_SupportsAlsSimilarityType() throws Exception {
        Movie movie = new Movie();
        movie.setId(2L);
        movie.setName("星际穿越");
        movie.setReason("ALS 隐语义相似，相似度 0.932");

        when(analyticsService.getSimilarMovies(1L, 3, 5)).thenReturn(List.of(movie));

        mockMvc.perform(get("/analytics/movies/1/similar")
                        .param("type", "3")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].reason").value("ALS 隐语义相似，相似度 0.932"));
    }
}
