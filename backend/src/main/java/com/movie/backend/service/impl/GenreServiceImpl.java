package com.movie.backend.service.impl;

import com.movie.backend.entity.Genre;
import com.movie.backend.mapper.GenreMapper;
import com.movie.backend.service.GenreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Genre Service Implementation with Redis Cache Management
 */
@Service
public class GenreServiceImpl implements GenreService {

    @Autowired
    private GenreMapper genreMapper;

    @Override
    @Cacheable(value = "movieMetadata", key = "'allGenresFull'")
    public List<Genre> getAllGenres() {
        return genreMapper.selectAll();
    }

    @Override
    public Genre getGenreById(Integer id) {
        return genreMapper.selectById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "movieMetadata", allEntries = true)
    public void addGenre(Genre genre) {
        // Check if genre already exists
        Genre existing = genreMapper.selectByName(genre.getName());
        if (existing != null) {
            throw new RuntimeException("Genre already exists: " + genre.getName());
        }
        genreMapper.insert(genre);
        // Cache eviction: Clears all movieMetadata cache including 'allGenres'
    }

    @Override
    @Transactional
    @CacheEvict(value = "movieMetadata", allEntries = true)
    public void updateGenre(Genre genre) {
        Genre existing = genreMapper.selectById(genre.getId());
        if (existing == null) {
            throw new RuntimeException("Genre not found: " + genre.getId());
        }
        genreMapper.update(genre);
        // Cache eviction: Clears all movieMetadata cache including 'allGenres'
    }

    @Override
    @Transactional
    @CacheEvict(value = "movieMetadata", allEntries = true)
    public void deleteGenre(Integer id) {
        Genre existing = genreMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Genre not found: " + id);
        }
        genreMapper.deleteById(id);
        // Cache eviction: Clears all movieMetadata cache including 'allGenres'
    }
}
