package com.movie.backend.controller;

import com.movie.backend.common.Result;
import com.movie.backend.dto.PublicUserVO;
import com.movie.backend.dto.UpdateUserProfileDTO;
import com.movie.backend.dto.UserProfileVO;
import com.movie.backend.dto.UserVO;
import com.movie.backend.entity.User;
import com.movie.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Management", description = "用户公开信息与个人资料管理接口")
@RestController
@RequestMapping("/users")
@org.springframework.validation.annotation.Validated
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(operationId = "getCurrentUserInfo", summary = "获取当前用户信息", description = "根据请求头中的 Token 获取当前登录用户的详细信息")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<UserVO> getInfo(@AuthenticationPrincipal User user) {
        UserVO userInfo = userService.getCurrentUserInfoWithStats(user.getId());
        if (userInfo == null) {
            return Result.notFound("用户不存在");
        }
        return Result.success(userInfo);
    }

    @Operation(operationId = "getPublicUserInfo", summary = "获取公开用户信息", description = "根据用户ID获取公开的用户信息（不包含敏感信息如邮箱、角色等）")
    @GetMapping("/{userId}")
    public Result<PublicUserVO> getPublicUserInfo(
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId) {
        PublicUserVO userInfo = userService.getPublicUserInfoWithStats(userId);
        if (userInfo == null) {
            return Result.notFound("用户不存在");
        }
        return Result.success(userInfo);
    }

    @Operation(operationId = "updateAvatar", summary = "更新用户头像", description = "更新当前登录用户的头像 URL")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me/avatar")
    public Result<String> updateAvatar(
            @Parameter(description = "头像图片的访问 URL", required = true)
            @RequestParam
            @jakarta.validation.constraints.NotBlank(message = "头像链接不能为空")
            @jakarta.validation.constraints.Size(max = 500, message = "头像链接长度不能超过500个字符")
            String avatarUrl,
            @AuthenticationPrincipal User user) {
        userService.updateAvatar(user.getId(), avatarUrl);
        return Result.success("头像更新成功");
    }

    @Operation(operationId = "getMyProfile", summary = "获取我的个人资料", description = "返回当前登录用户可编辑的资料信息，用于个人中心页面初始化")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/profile")
    public Result<UserProfileVO> getMyProfile(@AuthenticationPrincipal User user) {
        UserProfileVO profile = userService.getMyProfile(user.getId());
        if (profile == null) {
            return Result.notFound("用户不存在");
        }
        return Result.success(profile);
    }

    @Operation(operationId = "updateMyProfile", summary = "更新我的个人资料", description = "支持昵称、头像、主页、邮箱的增量更新，仅更新请求体中提供的字段")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me/profile")
    public Result<String> updateMyProfile(@Valid @RequestBody UpdateUserProfileDTO updateDTO,
                                          @AuthenticationPrincipal User user) {
        userService.updateMyProfile(user.getId(), updateDTO);
        return Result.success("个人资料更新成功");
    }
}
