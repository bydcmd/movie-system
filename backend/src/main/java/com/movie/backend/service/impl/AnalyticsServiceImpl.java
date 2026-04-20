package com.movie.backend.service.impl;

import com.movie.backend.common.TrendPeriod;
import com.movie.backend.dto.GenrePreferenceDTO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.dto.UserRetentionDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.mapper.AnalyticsMapper;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    private List<Movie> enrichMovies(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Movie> movies = movieMapper.selectByIds(movieIds);
        return movies == null ? new ArrayList<>() : movies;
    }

}
