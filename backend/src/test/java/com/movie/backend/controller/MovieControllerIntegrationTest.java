package com.movie.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.MovieSearchDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.service.AnalyticsService;
import com.movie.backend.service.MovieService;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MovieController 接口测试类
 * 测试重点：参数验证、异常处理、边界条件、正常业务逻辑
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("电影接口测试")
public class MovieControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieService movieService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Autowired
    private ObjectMapper objectMapper;

    private Movie testMovie;

    @BeforeEach
    void setUp() {
        testMovie = new Movie();
        testMovie.setId(1L);
        testMovie.setName("盗梦空间");
        testMovie.setScore(9.3);
        testMovie.setYear(2010);
        testMovie.setGenres("动作,科幻");
        testMovie.setStoryline("一部关于梦境的电影");
    }

    @Nested
    @DisplayName("获取电影详情接口")
    class GetDetailTests {

        @Test
        @DisplayName("正常获取电影详情 - 应返回200和电影数据")
        void testGetDetail_Success() throws Exception {
            when(movieService.getDetail(1L)).thenReturn(testMovie);

            mockMvc.perform(get("/movies/1"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("盗梦空间"))
                    .andExpect(jsonPath("$.data.score").value(9.3));
        }

        @Test
        @DisplayName("电影不存在 - 应返回404错误")
        void testGetDetail_NotFound() throws Exception {
            when(movieService.getDetail(999L)).thenReturn(null);

            mockMvc.perform(get("/movies/999"))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("电影不存在"));
        }

        @Test
        @DisplayName("电影ID为0 - 应返回400参数验证错误")
        void testGetDetail_InvalidId_Zero() throws Exception {
            mockMvc.perform(get("/movies/0"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("电影ID必须大于0"));
        }

        @Test
        @DisplayName("电影ID为负数 - 应返回400参数验证错误")
        void testGetDetail_InvalidId_Negative() throws Exception {
            mockMvc.perform(get("/movies/-1"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("高级搜索接口")
    class SearchTests {

        @Test
        @DisplayName("正常搜索 - 应返回分页数据")
        void testSearch_Success() throws Exception {
            MovieSearchDTO searchDTO = new MovieSearchDTO();
            searchDTO.setKeyword("盗梦");
            searchDTO.setPage(1);
            searchDTO.setSize(10);

            PageInfo<Movie> pageInfo = new PageInfo<>(Collections.singletonList(testMovie));
            when(movieService.search(any(MovieSearchDTO.class), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(pageInfo);

            mockMvc.perform(post("/movies/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(searchDTO)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.list[0].name").value("盗梦空间"));
        }

        @Test
        @DisplayName("关键词过长 - 应返回400参数验证错误")
        void testSearch_KeywordTooLong() throws Exception {
            MovieSearchDTO searchDTO = new MovieSearchDTO();
            searchDTO.setKeyword(StringUtils.repeat("a", 101)); // 超过100字符
            searchDTO.setPage(1);
            searchDTO.setSize(10);

            mockMvc.perform(post("/movies/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(searchDTO)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("关键词长度不能超过100"));
        }

        @Test
        @DisplayName("评分超出范围 - 应返回400参数验证错误")
        void testSearch_InvalidScore() throws Exception {
            MovieSearchDTO searchDTO = new MovieSearchDTO();
            searchDTO.setMinScore(11.0); // 超过10.0
            searchDTO.setPage(1);
            searchDTO.setSize(10);

            mockMvc.perform(post("/movies/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(searchDTO)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("最高评分不能大于10"));
        }

        @Test
        @DisplayName("年份格式错误 - 应返回400参数验证错误")
        void testSearch_InvalidYear() throws Exception {
            MovieSearchDTO searchDTO = new MovieSearchDTO();
            searchDTO.setYear("99"); // 格式错误
            searchDTO.setPage(1);
            searchDTO.setSize(10);

            mockMvc.perform(post("/movies/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(searchDTO)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("年份格式不正确，应为4位数字"));
        }

        @Test
        @DisplayName("页码为0 - 应返回400参数验证错误")
        void testSearch_InvalidPage() throws Exception {
            MovieSearchDTO searchDTO = new MovieSearchDTO();
            searchDTO.setKeyword("测试");
            searchDTO.setPage(0);
            searchDTO.setSize(10);

            mockMvc.perform(post("/movies/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(searchDTO)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("页码必须大于0"));
        }

        @Test
        @DisplayName("每页数量超过100 - 应返回400参数验证错误")
        void testSearch_SizeTooLarge() throws Exception {
            MovieSearchDTO searchDTO = new MovieSearchDTO();
            searchDTO.setKeyword("测试");
            searchDTO.setPage(1);
            searchDTO.setSize(101);

            mockMvc.perform(post("/movies/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(searchDTO)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("每页数量不能超过100"));
        }
    }

    @Nested
    @DisplayName("获取热门电影接口")
    class GetHotMoviesTests {

        @Test
        @DisplayName("正常获取热门电影 - 应返回电影列表")
        void testGetHotMovies_Success() throws Exception {
            List<Movie> movies = Collections.singletonList(testMovie);
            when(analyticsService.getHotMoviesByPeriod("DAILY", 10)).thenReturn(movies);

            mockMvc.perform(get("/movies/hot")
                            .param("limit", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].name").value("盗梦空间"));
        }

        @Test
        @DisplayName("默认参数 - 应使用默认limit=10")
        void testGetHotMovies_DefaultLimit() throws Exception {
            List<Movie> movies = Collections.singletonList(testMovie);
            when(analyticsService.getHotMoviesByPeriod(eq("DAILY"), anyInt())).thenReturn(movies);

            mockMvc.perform(get("/movies/hot"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("limit为0 - 应返回400参数验证错误")
        void testGetHotMovies_InvalidLimit_Zero() throws Exception {
            mockMvc.perform(get("/movies/hot")
                            .param("limit", "0"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("返回数量至少为1"));
        }

        @Test
        @DisplayName("limit超过100 - 应返回400参数验证错误")
        void testGetHotMovies_InvalidLimit_TooLarge() throws Exception {
            mockMvc.perform(get("/movies/hot")
                            .param("limit", "101"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("返回数量最多为100"));
        }
    }

    @Nested
    @DisplayName("按类型筛选电影接口")
    class GetMoviesByGenreTests {

        @Test
        @DisplayName("正常按类型筛选 - 应返回分页数据")
        void testGetMoviesByGenre_Success() throws Exception {
            PageInfo<Movie> pageInfo = new PageInfo<>(Collections.singletonList(testMovie));
            when(movieService.getMoviesByGenre(anyString(), anyInt(), anyInt())).thenReturn(pageInfo);

            mockMvc.perform(get("/movies/genres/动作")
                            .param("page", "1")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.list[0].name").value("盗梦空间"));
        }

        @Test
        @DisplayName("页码为0 - 应返回400参数验证错误")
        void testGetMoviesByGenre_InvalidPage() throws Exception {
            mockMvc.perform(get("/movies/genres/动作")
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("页码必须大于0"));
        }
    }

    @Nested
    @DisplayName("按年份筛选电影接口")
    class GetMoviesByYearTests {

        @Test
        @DisplayName("正常按年份筛选 - 应返回分页数据")
        void testGetMoviesByYear_Success() throws Exception {
            PageInfo<Movie> pageInfo = new PageInfo<>(Collections.singletonList(testMovie));
            when(movieService.getMoviesByYear(anyInt(), anyInt(), anyInt())).thenReturn(pageInfo);

            mockMvc.perform(get("/movies/years/2010")
                            .param("page", "1")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.list[0].year").value(2010));
        }

        @Test
        @DisplayName("年份早于1900 - 应返回400参数验证错误")
        void testGetMoviesByYear_InvalidYear() throws Exception {
            mockMvc.perform(get("/movies/years/1899")
                            .param("page", "1")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("年份不能早于1900"));
        }
    }

    @Nested
    @DisplayName("获取最新电影接口")
    class GetLatestMoviesTests {

        @Test
        @DisplayName("正常获取最新电影 - 应返回分页数据")
        void testGetLatestMovies_Success() throws Exception {
            List<Movie> movies = new ArrayList<>();
            movies.add(testMovie);
            PageInfo<Movie> pageInfo = new PageInfo<>(movies);

            when(movieService.getLatestMovies(anyInt(), anyInt())).thenReturn(pageInfo);

            mockMvc.perform(get("/movies/latest")
                            .param("page", "1")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.list[0].name").value("盗梦空间"));
        }

        @Test
        @DisplayName("默认分页参数 - 应使用默认page=1, size=10")
        void testGetLatestMovies_DefaultParams() throws Exception {
            PageInfo<Movie> pageInfo = new PageInfo<>(Collections.singletonList(testMovie));
            when(movieService.getLatestMovies(anyInt(), anyInt())).thenReturn(pageInfo);

            mockMvc.perform(get("/movies/latest"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }
}
