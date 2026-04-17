package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.common.UserStatus;
import com.movie.backend.dto.AdminDashboardTrendsVO;
import com.movie.backend.dto.AdminDashboardVO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.AdminDashboardMapper;
import com.movie.backend.mapper.CommentLikeMapper;
import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.mapper.FavoriteMapper;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.mapper.PersonMapper;
import com.movie.backend.mapper.RatingMapper;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.mapper.ViewHistoryMapper;
import com.movie.backend.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    private static final int COMMENT_STATUS_DRAFT = 1;
    private static final int COMMENT_STATUS_PUBLISHED = 2;
    private static final int COMMENT_STATUS_HIDDEN = 3;

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

    @Autowired
    private RatingMapper ratingMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private ViewHistoryMapper viewHistoryMapper;

    @Autowired
    private AdminDashboardMapper adminDashboardMapper;

    @Override
    public AdminDashboardVO getDashboardStats() {
        AdminDashboardVO dashboard = new AdminDashboardVO();
        dashboard.setOverview(adminDashboardMapper.selectOverview());

        AdminDashboardTrendsVO trends = new AdminDashboardTrendsVO();
        trends.setUserRegistrations(adminDashboardMapper.selectUserRegistrationTrend());
        trends.setPublishedComments(adminDashboardMapper.selectPublishedCommentTrend());
        trends.setFavorites(adminDashboardMapper.selectFavoriteTrend());
        trends.setRatings(adminDashboardMapper.selectRatingTrend());
        trends.setViews(adminDashboardMapper.selectViewTrend());
        trends.setWatchedMovies(adminDashboardMapper.selectWatchedTrend());
        dashboard.setTrends(trends);

        if (dashboard.getOverview() != null) {
            dashboard.setUserCount(dashboard.getOverview().getTotalUsers());
            dashboard.setMovieCount(dashboard.getOverview().getTotalMovies());
            dashboard.setCommentCount(dashboard.getOverview().getPublishedCommentCount());
        }
        return dashboard;
    }

    @Override
    @SuppressWarnings("resource")
    public PageInfo<User> getUserList(String keyword, Integer status, int page, int size) {
        PageHelper.startPage(page, size);
        List<User> list = userMapper.selectList(keyword, status);
        return new PageInfo<>(list);
    }

    @Override
    public void freezeUser(String id) {
        User existing = requireUser(id);
        if (UserStatus.isCancelled(existing.getStatus())) {
            throw new IllegalArgumentException("用户已注销，无法冻结");
        }
        if (UserStatus.isFrozen(existing.getStatus())) {
            throw new IllegalArgumentException("用户已冻结");
        }

        User updateUser = new User();
        updateUser.setId(id);
        updateUser.setStatus(UserStatus.FROZEN);
        updateUser.setPasswordVersion(resolvePasswordVersion(existing) + 1);
        updateUser.setUpdateTime(new Date());

        int affected = userMapper.update(updateUser);
        if (affected == 0) {
            throw new RuntimeException("用户冻结失败");
        }
    }

    @Override
    public void unfreezeUser(String id) {
        User existing = requireUser(id);
        if (UserStatus.isCancelled(existing.getStatus())) {
            throw new IllegalArgumentException("用户已注销，无法解冻");
        }
        if (UserStatus.isActive(existing.getStatus())) {
            throw new IllegalArgumentException("用户未被冻结");
        }

        User updateUser = new User();
        updateUser.setId(id);
        updateUser.setStatus(UserStatus.ACTIVE);
        updateUser.setUpdateTime(new Date());

        int affected = userMapper.update(updateUser);
        if (affected == 0) {
            throw new RuntimeException("用户解冻失败");
        }
    }

    @Override
    public void deleteUser(String id) {
        User existing = requireUser(id);
        if (UserStatus.isCancelled(existing.getStatus())) {
            throw new IllegalArgumentException("用户已注销");
        }

        User updateUser = new User();
        updateUser.setId(id);
        updateUser.setStatus(UserStatus.CANCELLED);
        updateUser.setNickname("已注销用户");
        updateUser.setPasswordVersion(resolvePasswordVersion(existing) + 1);
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
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "movieDetail", key = "#id"),
            @CacheEvict(value = "movieMetadata", key = "'allYears'")
    })
    public void deleteMovie(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("电影ID不能为空");
        }
        commentLikeMapper.deleteByMovieId(id);
        commentMapper.deleteByMovieId(id);
        ratingMapper.deleteByMovieId(id);
        favoriteMapper.deleteByMovieId(id);
        viewHistoryMapper.deleteByMovieId(id);
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
    public void hideComment(Long id) {
        Comment existing = requireComment(id);
        if (existing.getStatus() != null && existing.getStatus() == COMMENT_STATUS_DRAFT) {
            throw new IllegalArgumentException("草稿评论不能隐藏");
        }
        if (existing.getStatus() != null && existing.getStatus() == COMMENT_STATUS_HIDDEN) {
            throw new IllegalArgumentException("评论已隐藏");
        }

        int affected = commentMapper.updateStatusById(id, COMMENT_STATUS_HIDDEN);
        if (affected == 0) {
            throw new RuntimeException("评论隐藏失败");
        }
    }

    @Override
    public void restoreComment(Long id) {
        Comment existing = requireComment(id);
        if (existing.getStatus() != null && existing.getStatus() == COMMENT_STATUS_DRAFT) {
            throw new IllegalArgumentException("草稿评论不能恢复");
        }
        if (existing.getStatus() == null || existing.getStatus() == COMMENT_STATUS_PUBLISHED) {
            throw new IllegalArgumentException("评论未被隐藏");
        }
        if (existing.getStatus() != COMMENT_STATUS_HIDDEN) {
            throw new IllegalArgumentException("仅支持恢复已隐藏评论");
        }

        int affected = commentMapper.updateStatusById(id, COMMENT_STATUS_PUBLISHED);
        if (affected == 0) {
            throw new RuntimeException("评论恢复失败");
        }
    }

    @Override
    @Transactional
    public void deleteComment(Long id) {
        requireComment(id);
        int affected = commentMapper.deleteById(id);
        if (affected == 0) {
            throw new IllegalArgumentException("评论不存在");
        }
        commentLikeMapper.deleteByCommentId(id);
    }

    private User requireUser(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        User existing = userMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return existing;
    }

    private int resolvePasswordVersion(User user) {
        return user.getPasswordVersion() != null ? user.getPasswordVersion() : 1;
    }

    private Comment requireComment(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("评论ID不能为空");
        }

        Comment existing = commentMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        return existing;
    }
}
