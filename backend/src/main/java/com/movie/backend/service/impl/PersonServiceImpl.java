package com.movie.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.mapper.MovieMapper;
import com.movie.backend.mapper.PersonMapper;
import com.movie.backend.service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PersonServiceImpl implements PersonService {

    @Autowired
    private PersonMapper personMapper;

    @Autowired
    private MovieMapper movieMapper;

    @Override
    public Person getDetail(Long id) {
        return personMapper.selectById(id);
    }

    @Override
    public PageInfo<Person> getList(String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        List<Person> list = personMapper.selectList(keyword);
        return new PageInfo<>(list);
    }

    @Override
    public List<Movie> getMovies(Long id) {
        Person person = personMapper.selectById(id);
        if (person == null) {
            return new ArrayList<>();
        }
        return movieMapper.selectByPersonName(person.getName());
    }
}
