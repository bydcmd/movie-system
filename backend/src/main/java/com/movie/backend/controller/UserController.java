package com.movie.backend.controller;

import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.common.Result;
import com.movie.backend.dto.LoginDTO;
import com.movie.backend.dto.PublicUserVO;
import com.movie.backend.dto.RegisterDTO;
import com.movie.backend.dto.UpdateUserProfileDTO;
import com.movie.backend.dto.UserProfileVO;
import com.movie.backend.dto.UserVO;
import com.movie.backend.entity.User;
import com.movie.backend.service.TokenBlacklistService;
import com.movie.backend.service.UserService;
import com.movie.backend.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Tag(name = "用户管理", description = "用户登录、注册、个人信息管理相关接口")
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Operation(summary = "用户登录", description = "使用账号和密码登录，成功返回 Token 和用户信息")
    @PostMapping("/login")
    public Result<UserVO> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(userService.login(loginDTO));
    }

    @Operation(summary = "用户注册", description = "注册新用户，需要提供账号、密码、昵称等信息")
    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success("注册成功");
    }

    @Operation(summary = "获取当前用户信息", description = "根据请求头中的 Token 获取当前登录用户的详细信息")
    @GetMapping("/info")
    public Result<UserVO> getInfo(@CurrentUser User user) {
        UserVO userInfo = userService.getCurrentUserInfoWithStats(user.getId());
        if (userInfo == null) {
            return Result.fail(404, "用户不存在");
        }
        return Result.success(userInfo);
    }

    @Operation(summary = "获取公开用户信息", description = "根据用户ID获取公开的用户信息（不包含敏感信息如邮箱、角色等）")
    @GetMapping("/public/{userId}")
    public Result<PublicUserVO> getPublicUserInfo(
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId) {
        PublicUserVO userInfo = userService.getPublicUserInfoWithStats(userId);
        if (userInfo == null) {
            return Result.fail(404, "用户不存在");
        }
        return Result.success(userInfo);
    }

    @Operation(summary = "更新用户头像", description = "更新当前登录用户的头像 URL")
    @PutMapping("/avatar")
    public Result<String> updateAvatar(
            @Parameter(description = "头像图片的访问 URL", required = true)
            @RequestParam String avatarUrl,
            @CurrentUser User user) {
        userService.updateAvatar(user.getId(), avatarUrl);
        return Result.success("头像更新成功");
    }

    @Operation(summary = "获取我的个人资料", description = "返回当前登录用户可编辑的资料信息，用于个人中心页面初始化")
    @GetMapping("/profile")
    public Result<UserProfileVO> getMyProfile(@CurrentUser User user) {
        UserProfileVO profile = userService.getMyProfile(user.getId());
        if (profile == null) {
            return Result.fail(404, "用户不存在");
        }
        return Result.success(profile);
    }

    @Operation(summary = "更新我的个人资料", description = "支持昵称、头像、主页、邮箱的增量更新，仅更新请求体中提供的字段")
    @PutMapping("/profile")
    public Result<String> updateMyProfile(@Valid @RequestBody UpdateUserProfileDTO updateDTO,
                                          @CurrentUser User user) {
        userService.updateMyProfile(user.getId(), updateDTO);
        return Result.success("个人资料更新成功");
    }

    @Operation(summary = "刷新 Token", description = "使用 Refresh Token 换取新的 Access Token")
    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestParam String refreshToken) {
        try {
            String newAccessToken = JwtUtil.refreshAccessToken(refreshToken);
            return Result.success(newAccessToken);
        } catch (Exception e) {
            return Result.fail(401, "Token 刷新失败: " + e.getMessage());
        }
    }

    @Operation(summary = "用户注销", description = "注销当前用户，将 Token 加入黑名单")
    @PostMapping("/logout")
    public Result<String> logout(
            @Parameter(description = "Refresh Token", required = false) @RequestParam(required = false) String refreshToken,
            HttpServletRequest request) {
        try {
            // 获取 Access Token
            String accessToken = JwtUtil.extractTokenFromRequest(request);
            
            // 将 Access Token 和 Refresh Token 都加入黑名单
            tokenBlacklistService.blacklistBothTokens(accessToken, refreshToken);
            
            return Result.success("注销成功");
        } catch (Exception e) {
            return Result.fail(500, "注销失败: " + e.getMessage());
        }
    }

    @Operation(summary = "修改密码", description = "修改当前用户密码，修改后所有旧 Token 将失效")
    @PostMapping("/change-password")
    public Result<String> changePassword(
            @Parameter(description = "旧密码", required = true) @RequestParam String oldPassword,
            @Parameter(description = "新密码", required = true) @RequestParam String newPassword,
            @Parameter(description = "Refresh Token", required = false) @RequestParam(required = false) String refreshToken,
            @CurrentUser User user,
            HttpServletRequest request) {
        try {
            // 获取 Access Token
            String accessToken = JwtUtil.extractTokenFromRequest(request);
            
            // 修改密码（会递增 passwordVersion）
            userService.changePassword(user.getId(), oldPassword, newPassword);
            
            // 将当前的 Token 加入黑名单
            tokenBlacklistService.blacklistBothTokens(accessToken, refreshToken);
            
            return Result.success("密码修改成功，请重新登录");
        } catch (Exception e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @Operation(summary = "注销账号", description = "用户自行注销账号（软删除），保留历史评论和评分，但无法再登录")
    @PostMapping("/cancel")
    public Result<String> cancelAccount(
            @Parameter(description = "Refresh Token", required = false) @RequestParam(required = false) String refreshToken,
            @CurrentUser User user,
            HttpServletRequest request) {
        try {
            // 1. 执行账号软删除
            userService.cancelAccount(user.getId());

            // 2. 获取当前的 Access Token
            String accessToken = JwtUtil.extractTokenFromRequest(request);

            // 3. 将 Access Token 和 Refresh Token 加入黑名单，强制下线
            tokenBlacklistService.blacklistBothTokens(accessToken, refreshToken);

            return Result.success("账号已注销，感谢您的使用");
        } catch (Exception e) {
            return Result.fail(500, "注销失败: " + e.getMessage());
        }
    }
}

