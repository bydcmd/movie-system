package com.movie.backend.controller.admin;

import com.movie.backend.common.Result;
import com.movie.backend.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Recommendation Cache Management", description = "管理员推荐缓存管理接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/admin/recommendations/personalized/cache")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminRecommendationCacheController {

    @Autowired
    private AnalyticsService analyticsService;

    @Operation(operationId = "refreshPersonalizedRecommendationsByUserAdmin", summary = "刷新指定用户猜你喜欢缓存", description = "清除指定用户的猜你喜欢缓存，下一次请求将回源刷新")
    @DeleteMapping("/{targetUserId}")
    public Result<String> refreshPersonalizedRecommendationsByUser(
            @Parameter(description = "目标用户ID", required = true) @PathVariable @NotBlank String targetUserId) {
        String normalizedUserId = targetUserId.trim();
        if (normalizedUserId.isEmpty()) {
            return Result.badRequest("用户ID不能为空");
        }

        analyticsService.evictPersonalizedCache(normalizedUserId);
        return Result.success("已刷新用户猜你喜欢缓存: " + normalizedUserId);
    }

    @Operation(operationId = "refreshPersonalizedRecommendationsAllAdmin", summary = "刷新全量猜你喜欢缓存", description = "清除全部用户的猜你喜欢缓存，需显式确认后执行")
    @DeleteMapping("/all")
    public Result<String> refreshAllPersonalizedRecommendations(
            @Parameter(description = "确认清除全部用户缓存，必须传 true") @RequestParam(defaultValue = "false") boolean confirmAll) {
        if (!confirmAll) {
            return Result.badRequest("危险操作，请传 confirmAll=true 后重试");
        }

        analyticsService.evictPersonalizedCache(null);
        return Result.success("已刷新猜你喜欢全量缓存");
    }
}
