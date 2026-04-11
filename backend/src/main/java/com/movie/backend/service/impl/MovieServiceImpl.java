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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public PageInfo<Movie> search(MovieSearchDTO searchDTO, String userId) {
        MovieSearchDTO normalizedDTO = normalizeSearchDTO(searchDTO);

        // Start Page
        PageHelper.startPage(normalizedDTO.getPage(), normalizedDTO.getSize());
        
        // Query
        List<Movie> list = movieMapper.search(normalizedDTO);
        
        // Return PageInfo
        PageInfo<Movie> pageInfo = new PageInfo<>(list);
        return pageInfo;
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

    private CatalogQueryDTO normalizeCatalogQuery(CatalogQueryDTO catalogQuery) {
        if (catalogQuery == null) {
            return new CatalogQueryDTO();
        }

        if (catalogQuery.getMinScore() != null
                && catalogQuery.getMaxScore() != null
                && catalogQuery.getMinScore() > catalogQuery.getMaxScore()) {
            throw new IllegalArgumentException("最低评分不能大于最高评分");
        }

        if (catalogQuery.getStartYear() != null
                && catalogQuery.getEndYear() != null
                && catalogQuery.getStartYear() > catalogQuery.getEndYear()) {
            throw new IllegalArgumentException("起始年份不能大于结束年份");
        }

        return catalogQuery;
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
    @Cacheable(value = "movieMetadata", key = "'allLanguages'")
    public List<String> getAllLanguages() {
        // Use normalized language table
        // Cache: 24 hours TTL, cleared when languages are modified
        return movieMapper.selectAllLanguages();
    }

    @Override
    @Cacheable(value = "movieMetadata", key = "'allYears'")
    public List<Integer> getAllYears() {
        // Cache: 24 hours TTL, cleared when new movies are added
        return movieMapper.selectAllYears();
    }

    @Override
    public PageInfo<Movie> getCatalogMovies(CatalogQueryDTO catalogQuery) {
        CatalogQueryDTO normalizedQuery = normalizeCatalogQuery(catalogQuery);

        PageHelper.startPage(normalizedQuery.getPage(), normalizedQuery.getSize());
        List<Movie> list = movieMapper.selectByCatalog(normalizedQuery);

        return new PageInfo<>(list);
    }

    @Override
    public Map<String, Object> getFilterMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();

        List<Map<String, Object>> scoreSegments = new ArrayList<>();
        scoreSegments.add(createSegment("9分以上", 9.0, null));
        scoreSegments.add(createSegment("8-9分", 8.0, 9.0));
        scoreSegments.add(createSegment("7-8分", 7.0, 8.0));
        scoreSegments.add(createSegment("6-7分", 6.0, 7.0));
        scoreSegments.add(createSegment("6分以下", null, 6.0));
        metadata.put("scores", scoreSegments);

        List<Map<String, Object>> yearSegments = new ArrayList<>();
        yearSegments.add(createSegment("2020年代", 2020, null));
        yearSegments.add(createSegment("2010年代", 2010, 2019));
        yearSegments.add(createSegment("2000年代", 2000, 2009));
        yearSegments.add(createSegment("90年代", 1990, 1999));
        yearSegments.add(createSegment("80年代", 1980, 1989));
        yearSegments.add(createSegment("更早", null, 1979));
        metadata.put("eras", yearSegments);

        return metadata;
    }

    private Map<String, Object> createSegment(String label, Object min, Object max) {
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("label", label);
        segment.put("min", min);
        segment.put("max", max);
        return segment;
    }

}
