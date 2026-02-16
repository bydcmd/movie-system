package com.movie.backend.context;

import com.movie.backend.entity.User;

/**
 * 用户上下文管理
 * 使用 ThreadLocal 存储当前请求的用户信息
 * 避免在每个方法中手动解析 JWT Token
 */
public class UserContext {

    private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

    /**
     * 设置当前用户
     */
    public static void setCurrentUser(User user) {
        currentUser.set(user);
    }

    /**
     * 获取当前用户
     */
    public static User getCurrentUser() {
        return currentUser.get();
    }

    /**
     * 获取当前用户ID
     */
    public static String getCurrentUserId() {
        User user = currentUser.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 清除当前用户
     * 必须在请求结束时调用，避免内存泄漏
     */
    public static void clear() {
        currentUser.remove();
    }
}
