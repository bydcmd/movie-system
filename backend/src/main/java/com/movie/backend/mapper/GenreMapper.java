package com.movie.backend.mapper;

import com.movie.backend.entity.Genre;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Genre Mapper Interface
 */
@Mapper
public interface GenreMapper {
    /**
     * Get all genres
     */
    List<Genre> selectAll();

    /**
     * Get genre by ID
     */
    Genre selectById(Integer id);

    /**
     * Get genre by name
     */
    Genre selectByName(String name);

    /**
     * Insert genre
     */
    int insert(Genre genre);

    /**
     * Update genre
     */
    int update(Genre genre);

    /**
     * Delete genre by ID
     */
    int deleteById(Integer id);

    /**
     * Get genres by movie ID
     */
    List<Genre> selectByMovieId(Long movieId);
}
