package com.movie.backend.config;

import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.entity.User;
import com.movie.backend.exception.UnauthorizedException;
import com.movie.backend.mapper.UserMapper;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 当前用户参数解析器
 * 自动将 SecurityContext 中的用户信息注入到标注了 @CurrentUser 的方法参数中
 */
@Component
public class CurrentUserMethodArgumentResolver implements HandlerMethodArgumentResolver {

    @Autowired
    private UserMapper userMapper;

    /**
     * 判断是否支持该参数
     * 只有标注了 @CurrentUser 注解的 User 类型参数才支持
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class) 
               && parameter.getParameterType().equals(User.class);
    }

    /**
     * 解析参数值
     * 从 SecurityContext 获取当前用户信息
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {

        User user = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User principalUser) {
                user = principalUser;
            } else if (principal instanceof String userId && !userId.trim().isEmpty() && !"anonymousUser".equals(userId)) {
                // 兼容历史 principal 为 userId 字符串的场景
                user = userMapper.selectById(userId);
            }
        }

        // 检查是否必需
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        if (annotation != null && annotation.required() && user == null) {
            throw new UnauthorizedException("用户未登录或 Token 无效");
        }

        return user;
    }
}
