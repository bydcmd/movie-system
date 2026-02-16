package com.movie.backend.config;

import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.context.UserContext;
import com.movie.backend.entity.User;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 当前用户参数解析器
 * 自动将 ThreadLocal 中的用户信息注入到标注了 @CurrentUser 的方法参数中
 */
@Component
public class CurrentUserMethodArgumentResolver implements HandlerMethodArgumentResolver {

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
     * 从 ThreadLocal 中获取当前用户信息
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, 
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, 
                                  WebDataBinderFactory binderFactory) throws Exception {
        
        User user = UserContext.getCurrentUser();
        
        // 检查是否必需
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        if (annotation != null && annotation.required() && user == null) {
            throw new RuntimeException("用户未登录或 Token 无效");
        }
        
        return user;
    }
}
