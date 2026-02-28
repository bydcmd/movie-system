package com.movie.backend.controller;

import com.movie.backend.common.Result;
import com.movie.backend.dto.ChangePasswordDTO;
import com.movie.backend.dto.LoginDTO;
import com.movie.backend.dto.RegisterDTO;
import com.movie.backend.dto.UserVO;
import com.movie.backend.entity.User;
import com.movie.backend.service.TokenBlacklistService;
import com.movie.backend.service.UserService;
import com.movie.backend.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "Auth Management", description = "登录、注册、令牌刷新与账号安全相关接口")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @Value("${app.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.auth.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

    @Value("${jwt.refresh-token-expiration:2592000000}")
    private long refreshTokenExpirationMs;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Operation(operationId = "login", summary = "用户登录", description = "使用账号和密码登录，成功返回 Token 和用户信息")
    @PostMapping("/login")
    public Result<UserVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        UserVO userVO = userService.login(loginDTO);
        if (StringUtils.hasText(userVO.getRefreshToken())) {
            writeRefreshTokenCookie(response, userVO.getRefreshToken());
            userVO.setRefreshToken(null);
        }
        return Result.success(userVO);
    }

    @Operation(operationId = "register", summary = "用户注册", description = "注册新用户，需要提供账号、密码、昵称等信息")
    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success("注册成功");
    }

    @Operation(operationId = "refreshToken", summary = "刷新 Token", description = "使用 HttpOnly Cookie 中的 Refresh Token 换取新的 Access Token")
    @PostMapping("/token/refresh")
    public Result<String> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (!StringUtils.hasText(refreshToken)) {
            return Result.unauthorized("缺少 Refresh Token，请重新登录");
        }

        try {
            // 已撤销的 Refresh Token 不允许继续换发 Access Token
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                clearRefreshTokenCookie(response);
                return Result.unauthorized("Refresh Token 已失效，请重新登录");
            }

            String newAccessToken = JwtUtil.refreshAccessToken(refreshToken);
            // 续期 Cookie 生命周期
            writeRefreshTokenCookie(response, refreshToken);
            return Result.success(newAccessToken);
        } catch (Exception e) {
            clearRefreshTokenCookie(response);
            return Result.unauthorized("Refresh Token 已失效，请重新登录");
        }
    }

    @Operation(operationId = "logout", summary = "用户登出", description = "注销当前用户，将 Token 加入黑名单")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public Result<String> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String accessToken = JwtUtil.extractTokenFromRequest(request);
        String refreshToken = extractRefreshTokenFromCookie(request);

        tokenBlacklistService.blacklistBothTokens(accessToken, refreshToken);
        clearRefreshTokenCookie(response);

        return Result.success("注销成功");
    }

    @Operation(operationId = "changePassword", summary = "修改密码", description = "修改当前用户密码，修改后所有旧 Token 将失效")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me/password")
    public Result<String> changePassword(
            @Valid @RequestBody ChangePasswordDTO changePasswordDTO,
            @AuthenticationPrincipal User user,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            String accessToken = JwtUtil.extractTokenFromRequest(request);
            String refreshToken = extractRefreshTokenFromCookie(request);

            userService.changePassword(user.getId(), changePasswordDTO.getOldPassword(), changePasswordDTO.getNewPassword());

            tokenBlacklistService.blacklistBothTokens(accessToken, refreshToken);
            clearRefreshTokenCookie(response);

            return Result.success("密码修改成功，请重新登录");
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @Operation(operationId = "cancelAccount", summary = "注销账号", description = "用户自行注销账号（软删除），保留历史评论和评分，但无法再登录")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/me")
    public Result<String> cancelAccount(
            @AuthenticationPrincipal User user,
            HttpServletRequest request,
            HttpServletResponse response) {
        userService.cancelAccount(user.getId());

        String accessToken = JwtUtil.extractTokenFromRequest(request);
        String refreshToken = extractRefreshTokenFromCookie(request);

        tokenBlacklistService.blacklistBothTokens(accessToken, refreshToken);
        clearRefreshTokenCookie(response);

        return Result.success("账号已注销，感谢您的使用");
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/auth")
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
