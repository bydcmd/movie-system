package com.movie.backend.config;

import com.movie.backend.context.UserContext;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.service.TokenBlacklistService;
import com.movie.backend.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;

/**
 * JWT 拦截器
 * 负责验证 Token 并设置 Spring Security 上下文
 * 同时将用户信息存入 ThreadLocal，供 @CurrentUser 注解使用
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 提取 Token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // Token 为空或无效时，直接放行，但不设置用户上下文
        // 这样可以支持「可选鉴权」：Controller 可以通过 @CurrentUser 判断用户是否登录
        if (token == null || !JwtUtil.validateToken(token)) {
            return true;  // 放行，不设置用户上下文
        }

        // 检查 Token 是否在黑名单中（已被撤销）
        // 已撤销的 Token 视为无效，放行但不设置用户上下文
        if (tokenBlacklistService.isBlacklisted(token)) {
            return true;  // 放行，不设置用户上下文
        }

        // Token 有效，解析用户信息并设置 Spring Security 上下文
        try {
            Claims claims = JwtUtil.parseToken(token);
            String userId = claims.getSubject();
            if (userId == null || userId.trim().isEmpty()) {
                return true;
            }

            User user = userMapper.selectById(userId);
            if (user == null) {
                return true;
            }

            Integer status = user.getStatus();
            if (status != null && status != 0) {
                return true;
            }

            Integer tokenPasswordVersion = claims.get("passwordVersion", Integer.class);
            Integer currentPasswordVersion = user.getPasswordVersion() != null ? user.getPasswordVersion() : 1;
            if (tokenPasswordVersion == null || !tokenPasswordVersion.equals(currentPasswordVersion)) {
                return true;
            }

            Integer role = user.getRole();

            // 根据 role 设置权限（0=ADMIN, 1=USER）
            String authority = (role != null && role == 0) ? "ROLE_ADMIN" : "ROLE_USER";
            SimpleGrantedAuthority grantedAuthority = new SimpleGrantedAuthority(authority);

            // 创建 Authentication 对象并设置到 SecurityContext
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(grantedAuthority)
                );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 将用户信息存入 ThreadLocal
            UserContext.setCurrentUser(user);
        } catch (Exception e) {
            // Token 解析失败，放行但不设置用户上下文
            return true;
        }

        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理 SecurityContext，避免线程池复用导致的上下文泄露
        SecurityContextHolder.clearContext();
        // 清理 ThreadLocal，避免内存泄漏
        UserContext.clear();
    }
}

