package com.movie.backend.controller.admin;

import com.movie.backend.common.Result;
import com.movie.backend.dto.AdminDashboardVO;
import com.movie.backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard Management", description = "管理员仪表盘统计接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminDashboardController {

    @Autowired
    private AdminService adminService;

    @Operation(operationId = "getDashboardStats", summary = "获取仪表盘统计数据", description = "返回后台总览统计与近7天趋势数据")
    @GetMapping("/stats")
    public Result<AdminDashboardVO> getStats() {
        return Result.success(adminService.getDashboardStats());
    }
}
