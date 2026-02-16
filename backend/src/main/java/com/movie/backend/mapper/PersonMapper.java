package com.movie.backend.mapper;

import com.movie.backend.entity.Person;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PersonMapper {
    int insert(Person person);

    int update(Person person);

    int deleteById(@Param("id") Long id);

    Person selectById(@Param("id") Long id);

    List<Person> selectList(@Param("keyword") String keyword);
}
