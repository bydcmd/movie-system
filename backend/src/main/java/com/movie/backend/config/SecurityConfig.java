package com.movie.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


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

    @Autowired
    private CustomAuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（因为使用 JWT，不需要 CSRF 保护）
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())

            // 禁用 Spring Security 的默认登录页和表单登录
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // 无状态 Session（使用 JWT，不需要 Session）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 接口访问控制：默认需要认证，仅对公开接口放行
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/favicon.ico", "/error", "/swagger-ui/**", "/v3/api-docs/**", "/images/**").permitAll()
                .requestMatchers("/auth/login", "/auth/register", "/auth/token/refresh").permitAll()
                .requestMatchers("/auth/logout", "/auth/me", "/auth/me/**").authenticated()
                .requestMatchers("/users/me", "/users/me/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/movies/*/comments/me", "/movies/*/long-reviews/me", "/comments/me/**", "/favorite-folders/count").authenticated()
                .requestMatchers(HttpMethod.GET, "/users/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/movies/**", "/people/**", "/analytics/**", "/comments/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/movies/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/favorite-folders/*").permitAll()
                .requestMatchers("/admin/**", "/favorites/**", "/ratings/**", "/view-histories/**", "/watched-movies/**", "/files/**", "/favorite-folders/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/comments/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/comments/**").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/comments/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/comments/**").authenticated()
                .anyRequest().authenticated())

            // 在鉴权前注入 JWT 认证信息
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // 自定义未认证与无权限响应
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }
}
