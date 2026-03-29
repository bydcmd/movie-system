package com.movie.backend.mapper;

import com.movie.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    /**
     * Select user by ID (Check existence or login)
     */
    User selectById(@Param("id") String id);

    /**
     * Select user by Email (Optional validation)
     */
    User selectByEmail(@Param("email") String email);

    /**
     * Insert new user
     */
    int insert(User user);

    /**
     * Search Users (Admin)
     */
    List<User> selectList(@Param("keyword") String keyword, @Param("status") Integer status);

    /**
     * Delete User
     */
    int deleteById(@Param("id") String id);

    /**
     * Update User
     */
    int update(User user);

    /**
     * Count active (non-cancelled) users.
     */
    int countActiveUsers();
}
