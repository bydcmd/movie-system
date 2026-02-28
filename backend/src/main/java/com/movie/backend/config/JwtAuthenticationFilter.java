package com.movie.backend.config;

import com.movie.backend.entity.User;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.service.TokenBlacklistService;
import com.movie.backend.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在 Spring Security 过滤器链中解析 JWT，并写入 SecurityContext。
 * 这样 requestMatchers(...).authenticated() 能基于 Bearer Token 生效。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (JwtUtil.validateToken(token)
                        && !tokenBlacklistService.isBlacklisted(token)
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Claims claims = JwtUtil.parseToken(token);
                    String userId = claims.getSubject();
                    if (userId != null && !userId.trim().isEmpty()) {
                        User user = userMapper.selectById(userId);
                        if (user != null) {
                            Integer status = user.getStatus();
                            if (status == null || status == 0) {
                                Integer tokenPasswordVersion = claims.get("passwordVersion", Integer.class);
                                Integer currentPasswordVersion = user.getPasswordVersion() != null
                                        ? user.getPasswordVersion() : 1;
                                if (tokenPasswordVersion != null && tokenPasswordVersion.equals(currentPasswordVersion)) {
                                    Integer role = user.getRole();
                                    String authority = (role != null && role == 0) ? "ROLE_ADMIN" : "ROLE_USER";
                                    UsernamePasswordAuthenticationToken authentication =
                                            new UsernamePasswordAuthenticationToken(
                                                    user,
                                                    null,
                                                    Collections.singletonList(new SimpleGrantedAuthority(authority))
                                            );
                                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                    SecurityContextHolder.getContext().setAuthentication(authentication);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 与当前项目风格一致：Token 异常视为未登录，不中断请求流程
        }

        filterChain.doFilter(request, response);
    }
}
