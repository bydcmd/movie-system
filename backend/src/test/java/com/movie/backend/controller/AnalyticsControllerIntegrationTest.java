package com.movie.backend.controller;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

}
