package com.movie.backend.service;

import com.github.pagehelper.PageInfo;
import com.movie.backend.entity.Person;

public interface PersonService {
    /**
     * 根据ID获取影人详情
     * @param id 影人ID
     * @return 影人详情
     */
    Person getDetail(Long id);

    /**
     * 搜索/分页查询影人列表
     * @param keyword 关键词
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    PageInfo<Person> getList(String keyword, int page, int size);

    /**
     * 获取影人参与的电影
     * @param id 影人ID
     * @return 电影列表
     */
    java.util.List<com.movie.backend.entity.Movie> getMovies(Long id);
}
