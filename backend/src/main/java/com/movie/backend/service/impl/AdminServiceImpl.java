package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.CommentLikeMapper;
import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.mapper.PersonMapper;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MovieMapper movieMapper;

    @Autowired
    private PersonMapper personMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private CommentLikeMapper commentLikeMapper;

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userMapper.countActiveUsers());
        stats.put("movieCount", movieMapper.countAll());
        stats.put("commentCount", commentMapper.countAll());
        return stats;
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<User> getUserList(String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        List<User> list = userMapper.selectList(keyword);
        return new PageInfo<>(list);
    }

    @Override
    public void deleteUser(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        User existing = userMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (existing.getStatus() != null && existing.getStatus() == 2) {
            throw new IllegalArgumentException("用户已注销");
        }

        Integer currentPasswordVersion = existing.getPasswordVersion() != null ? existing.getPasswordVersion() : 1;

        User updateUser = new User();
        updateUser.setId(id);
        updateUser.setStatus(2);
        updateUser.setNickname("已注销用户");
        updateUser.setPasswordVersion(currentPasswordVersion + 1);
        updateUser.setUpdateTime(new Date());

        int affected = userMapper.update(updateUser);
        if (affected == 0) {
            throw new RuntimeException("用户注销失败");
        }
    }

    @Override
    @CacheEvict(value = "movieMetadata", key = "'allYears'")
    public void addMovie(Movie movie) {
        if (movie == null) {
            throw new IllegalArgumentException("电影信息不能为空");
        }
        if (movie.getId() == null) {
            movie.setId(System.currentTimeMillis());
        }
        int affected = movieMapper.insert(movie);
        if (affected == 0) {
            throw new RuntimeException("电影添加失败");
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "movieDetail", key = "#movie.id"),
            @CacheEvict(value = "movieMetadata", key = "'allYears'"),
            @CacheEvict(value = "movieMetadata", key = "'allGenres'"),
            @CacheEvict(value = "movieMetadata", key = "'allRegions'")
    })
    public void updateMovie(Movie movie) {
        if (movie == null || movie.getId() == null) {
            throw new IllegalArgumentException("电影ID不能为空");
        }
        int affected = movieMapper.update(movie);
        if (affected == 0) {
            throw new IllegalArgumentException("电影不存在");
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "movieDetail", key = "#id"),
            @CacheEvict(value = "movieMetadata", key = "'allYears'")
    })
    public void deleteMovie(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("电影ID不能为空");
        }
        int affected = movieMapper.deleteById(id);
        if (affected == 0) {
            throw new IllegalArgumentException("电影不存在");
        }
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<Movie> getMovieList(String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        List<Movie> list = movieMapper.selectList(keyword);
        return new PageInfo<>(list);
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<Person> getPersonList(String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        List<Person> list = personMapper.selectList(keyword);
        return new PageInfo<>(list);
    }

    @Override
    public void addPerson(Person person) {
        if (person == null) {
            throw new IllegalArgumentException("影人信息不能为空");
        }
        if (person.getId() == null) {
            person.setId(System.currentTimeMillis());
        }
        int affected = personMapper.insert(person);
        if (affected == 0) {
            throw new RuntimeException("影人添加失败");
        }
    }

    @Override
    public void updatePerson(Person person) {
        if (person == null || person.getId() == null) {
            throw new IllegalArgumentException("影人ID不能为空");
        }
        int affected = personMapper.update(person);
        if (affected == 0) {
            throw new IllegalArgumentException("影人不存在");
        }
    }

    @Override
    public void deletePerson(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("影人ID不能为空");
        }
        int affected = personMapper.deleteById(id);
        if (affected == 0) {
            throw new IllegalArgumentException("影人不存在");
        }
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<Comment> getCommentList(String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        List<Comment> list = commentMapper.selectList(keyword);
        return new PageInfo<>(list);
    }

    @Override
    @Transactional
    public void deleteComment(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("评论ID不能为空");
        }
        int affected = commentMapper.deleteById(id);
        if (affected == 0) {
            throw new IllegalArgumentException("评论不存在");
        }
        commentLikeMapper.deleteByCommentId(id);
    }
}
