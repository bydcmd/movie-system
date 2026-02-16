package com.movie.backend.service;

import com.movie.backend.entity.Genre;

import java.util.List;

/**
 * Genre Service Interface
 */
public interface GenreService {
    /**
     * Get all genres
     */
    List<Genre> getAllGenres();

    /**
     * Get genre by ID
     */
    Genre getGenreById(Integer id);

    /**
     * Add new genre
     */
    void addGenre(Genre genre);

    /**
     * Update genre
     */
    void updateGenre(Genre genre);

    /**
     * Delete genre by ID
     */
    void deleteGenre(Integer id);
}
