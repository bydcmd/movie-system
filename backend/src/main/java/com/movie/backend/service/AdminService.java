package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.dto.AdminDashboardVO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.entity.User;

public interface AdminService {
    // --- Dashboard ---
    AdminDashboardVO getDashboardStats();

    // --- User Management ---
    PageInfo<User> getUserList(String keyword, Integer status, int page, int size);
    void freezeUser(String id);
    void unfreezeUser(String id);
    void deleteUser(String id);

    // --- Movie Management ---
    void addMovie(Movie movie);
    void updateMovie(Movie movie);
    void deleteMovie(Long id);
    PageInfo<Movie> getMovieList(String keyword, int page, int size);

    // --- Person Management ---
    PageInfo<Person> getPersonList(String keyword, int page, int size);
    void addPerson(Person person);
    void updatePerson(Person person);
    void deletePerson(Long id);

    // --- Comment Management ---
    PageInfo<Comment> getCommentList(String keyword, int page, int size);
    void hideComment(Long id);
    void restoreComment(Long id);
    void deleteComment(Long id);
}
