package com.movie.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 * 启用方法级别的权限控制，使用 @PreAuthorize 注解
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（因为使用 JWT，不需要 CSRF 保护）
            .csrf(AbstractHttpConfigurer::disable)

            // 禁用 Spring Security 的默认登录页和表单登录
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // 无状态 Session（使用 JWT，不需要 Session）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 所有请求都放行，具体的权限控制由 JwtInterceptor 和 @PreAuthorize 注解处理
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

            // 自定义访问拒绝处理器
            .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }
}
