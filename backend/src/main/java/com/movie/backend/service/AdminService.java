package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.entity.User;

import java.util.Map;

public interface AdminService {
    // --- Dashboard ---
    Map<String, Object> getDashboardStats();

    // --- User Management ---
    PageInfo<User> getUserList(String keyword, int page, int size);
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
    void deleteComment(Long id);
}
