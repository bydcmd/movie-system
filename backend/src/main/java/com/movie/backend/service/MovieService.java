package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.CatalogQueryDTO;
import com.movie.backend.dto.MovieSearchDTO;
import com.movie.backend.entity.Movie;

import java.util.List;

import java.util.Map;

public interface MovieService {
    /**
     * Get Movie Detail
     */
    Movie getDetail(Long id);

    /**
     * Search Movies (Paged)
     */
    PageInfo<Movie> search(MovieSearchDTO searchDTO, String userId);

    /**
     * Get Hot Movies (By votes)
     */
    List<Movie> getHotMovies(int limit);

    /**
     * Get Recommended Movies (By score)
     */
    List<Movie> getRecommendedMovies(int limit);

    /**
     * Get Movies by Genre (Paged)
     */
    PageInfo<Movie> getMoviesByGenre(String genre, int page, int size);

    /**
     * Get Movies by Year (Paged)
     */
    PageInfo<Movie> getMoviesByYear(Integer year, int page, int size);

    /**
     * Get Latest Movies (Paged)
     */
    PageInfo<Movie> getLatestMovies(int page, int size);

    /**
     * 获取所有分类类型
     */
    List<String> getAllGenres();

    /**
     * 获取所有地区
     */
    List<String> getAllRegions();

    /**
     * 获取所有语言
     */
    List<String> getAllLanguages();

    /**
     * 获取所有年份
     */
    List<Integer> getAllYears();

    /**
     * Get Movies Catalog (用于目录浏览页面的GET请求)
     * 与复杂搜索分离，专注于简单的分类筛选
     */
    PageInfo<Movie> getCatalogMovies(CatalogQueryDTO catalogQuery);

    /**
     * 获取电影筛选元数据（评分分段、年代分段）
     */
    Map<String, Object> getFilterMetadata();

}
