package com.movie.backend.service.impl;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.GenrePreferenceDTO;
import com.movie.backend.dto.SimilarMovieDTO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.dto.UserRetentionDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.mapper.AnalyticsMapper;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    @Autowired
    private AnalyticsMapper analyticsMapper;

    @Autowired
    private MovieMapper movieMapper;

    @Override
    public List<Movie> getHotMoviesByPeriod(String period, int limit) {
        List<Long> movieIds = analyticsMapper.selectHotMovieIds(period.toUpperCase(), limit);
        return enrichMovies(movieIds);
    }

    @Override
    public List<TrendingMovieDTO> getTrendingMovies(TrendPeriod period, int limit) {
        List<TrendingMovieDTO> results = movieMapper.selectTrendingWithScore(period.name(), limit);
        if (results == null) {
            return new ArrayList<>();
        }
        int rank = 1;
        for (TrendingMovieDTO dto : results) {
            dto.setRank(rank++);
        }
        return results;
    }

    @Override
    public List<Movie> getSimilarMovies(Long movieId, Integer similarityType, int limit) {
        List<SimilarMovieDTO> dtos = analyticsMapper.selectSimilarMovies(movieId, similarityType, limit);
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = dtos.stream().map(SimilarMovieDTO::getSimilarMovieId).collect(Collectors.toList());
        Map<Long, Movie> movieMap = batchLoadMovies(ids);

        List<Movie> result = new ArrayList<>();
        for (SimilarMovieDTO dto : dtos) {
            Movie movie = movieMap.get(dto.getSimilarMovieId());
            if (movie == null) {
                continue;
            }
            movie.setReason(buildSimilarReason(dto.getSimilarityType(), dto.getSimilarityScore()));
            result.add(movie);
        }
        return result;
    }

    @Override
    public List<UserRetentionDTO> getUserRetention(int limit) {
        List<UserRetentionDTO> results = analyticsMapper.selectUserRetention(limit);
        return results == null ? new ArrayList<>() : results;
    }

    @Override
    public List<GenrePreferenceDTO> getGenrePreference(int limit) {
        List<GenrePreferenceDTO> results = analyticsMapper.selectGenrePreference(limit);
        return results == null ? new ArrayList<>() : results;
    }

    // ---- private helpers ----

    private Map<Long, Movie> batchLoadMovies(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Movie> movies = movieMapper.selectByIds(ids);
        return movies.stream().collect(Collectors.toMap(Movie::getId, Function.identity(), (a, b) -> a));
    }

    private List<Movie> enrichMovies(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Movie> movies = movieMapper.selectByIds(movieIds);
        return movies == null ? new ArrayList<>() : movies;
    }

    private String buildSimilarReason(Integer similarityType, Double similarityScore) {
        String typeText = "相似推荐";
        if (similarityType != null) {
            if (similarityType == 1) {
                typeText = "内容相似";
            } else if (similarityType == 2) {
                typeText = "协同过滤相似";
            }
        }
        if (similarityScore == null) {
            return typeText + "结果";
        }
        return typeText + "，相似度 " + String.format(Locale.ROOT, "%.3f", similarityScore);
    }
}
