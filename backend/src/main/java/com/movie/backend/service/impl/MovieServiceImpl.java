package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.CatalogQueryDTO;
import com.movie.backend.dto.MovieSearchDTO;
import com.movie.backend.entity.Genre;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Region;
import com.movie.backend.mapper.GenreMapper;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.mapper.RegionMapper;
import com.movie.backend.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MovieServiceImpl implements MovieService {

    @Autowired
    private MovieMapper movieMapper;

    @Autowired
    private GenreMapper genreMapper;

    @Autowired
    private RegionMapper regionMapper;

    @Override
    @Cacheable(value = "movieDetail", key = "#id")
    public Movie getDetail(Long id) {
        // Return null instead of throwing exception to allow caching null values
        // This prevents cache penetration attacks where malicious users repeatedly
        // request non-existent movie IDs (e.g., -1 or 99999999)
        // Controller layer will handle null and return 404 response
        Movie movie = movieMapper.selectById(id);
        return movie;  // Returns null if not found, Spring Cache will cache it
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<Movie> search(MovieSearchDTO searchDTO) {
        MovieSearchDTO normalizedDTO = normalizeSearchDTO(searchDTO);

        // Start Page
        PageHelper.startPage(normalizedDTO.getPage(), normalizedDTO.getSize());
        
        // Query
        List<Movie> list = movieMapper.search(normalizedDTO);
        
        // Return PageInfo
        return new PageInfo<>(list);
    }

    private MovieSearchDTO normalizeSearchDTO(MovieSearchDTO searchDTO) {
        if (searchDTO == null) {
            return new MovieSearchDTO();
        }

        searchDTO.setKeyword(normalizeText(searchDTO.getKeyword()));
        searchDTO.setYear(normalizeText(searchDTO.getYear()));
        searchDTO.setSortBy(normalizeText(searchDTO.getSortBy()));
        searchDTO.setSortOrder(normalizeText(searchDTO.getSortOrder()));

        if (searchDTO.getSortOrder() == null) {
            searchDTO.setSortOrder("desc");
        }

        searchDTO.setGenres(cleanStringList(searchDTO.getGenres()));
        searchDTO.setRegions(cleanStringList(searchDTO.getRegions()));
        searchDTO.setDirectors(cleanStringList(searchDTO.getDirectors()));
        searchDTO.setActors(cleanStringList(searchDTO.getActors()));

        if (searchDTO.getMinScore() != null
                && searchDTO.getMaxScore() != null
                && searchDTO.getMinScore() > searchDTO.getMaxScore()) {
            throw new IllegalArgumentException("最低评分不能大于最高评分");
        }

        if (searchDTO.getStartYear() != null
                && searchDTO.getEndYear() != null
                && searchDTO.getStartYear() > searchDTO.getEndYear()) {
            throw new IllegalArgumentException("起始年份不能大于结束年份");
        }

        return searchDTO;
    }

    private List<String> cleanStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        List<String> cleaned = values.stream()
                .map(this::normalizeText)
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public List<Movie> getHotMovies(int limit) {
        return movieMapper.selectHot(limit);
    }

    @Override
    public List<Movie> getRecommendedMovies(int limit) {
        return movieMapper.selectRecommended(limit);
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<Movie> getMoviesByGenre(String genre, int page, int size) {
        PageHelper.startPage(page, size);
        List<Movie> list = movieMapper.selectByGenre(genre);
        return new PageInfo<>(list);
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<Movie> getMoviesByYear(Integer year, int page, int size) {
        PageHelper.startPage(page, size);
        List<Movie> list = movieMapper.selectByYear(year);
        return new PageInfo<>(list);
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<Movie> getLatestMovies(int page, int size) {
        PageHelper.startPage(page, size);
        List<Movie> list = movieMapper.selectLatest();
        return new PageInfo<>(list);
    }

    @Override
    @Cacheable(value = "movieMetadata", key = "'allGenres'")
    public List<String> getAllGenres() {
        // Use normalized genre table
        // Cache: 24 hours TTL, cleared when genres are modified
        List<Genre> genres = genreMapper.selectAll();
        return genres.stream()
                .map(Genre::getName)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "movieMetadata", key = "'allRegions'")
    public List<String> getAllRegions() {
        // Use normalized region table
        // Cache: 24 hours TTL, cleared when regions are modified
        List<Region> regions = regionMapper.selectAll();
        return regions.stream()
                .map(Region::getName)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "movieMetadata", key = "'allYears'")
    public List<Integer> getAllYears() {
        // Cache: 24 hours TTL, cleared when new movies are added
        return movieMapper.selectAllYears();
    }

    /**
     * Note: When implementing genre/region/movie modification operations, add @CacheEvict:
     * - For genre changes: @CacheEvict(value = "movieMetadata", key = "'allGenres'")
     * - For region changes: @CacheEvict(value = "movieMetadata", key = "'allRegions'")
     * - For new movies: @CacheEvict(value = "movieMetadata", key = "'allYears'")
     * - For movie detail updates: @CacheEvict(value = "movieDetail", key = "#movieId")
     */

    @Override
    public PageInfo<Movie> getCatalogMovies(CatalogQueryDTO catalogQuery) {
        // 1. 开启分页 (直接使用入参中的 page 和 size)
        PageHelper.startPage(catalogQuery.getPage(), catalogQuery.getSize());

        // 2. 调用 Mapper (使用注入的 movieMapper，而不是 baseMapper)
        // 注意：请确保 MovieMapper 接口中已经添加了 List<Movie> selectByCatalog(CatalogQueryDTO dto); 方法
        List<Movie> list = movieMapper.selectByCatalog(catalogQuery);

        // 3. 返回 PageInfo (保持与接口定义一致)
        return new PageInfo<>(list);
    }

}
