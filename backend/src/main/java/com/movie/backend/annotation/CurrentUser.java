package com.movie.backend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当前用户注解
 * 用于 Controller 方法参数，自动注入当前登录用户信息
 * 
 * 使用示例:
 * @GetMapping("/info")
 * public Result<User> getUserInfo(@CurrentUser User user) {
 *     return Result.success(user);
 * }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
    
    /**
     * 是否必需
     * true: 如果用户未登录则抛出异常
     * false: 允许用户为 null
     */
    boolean required() default true;
}
