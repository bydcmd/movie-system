package com.movie.backend.controller.admin;

import com.github.pagehelper.PageInfo;
import com.movie.backend.common.Result;
import com.movie.backend.entity.User;
import com.movie.backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin User Management", description = "管理员用户管理接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminUserController {

    @Autowired
    private AdminService adminService;

    @Operation(operationId = "getUserListAdmin", summary = "用户列表管理", description = "分页查询所有注册用户")
    @GetMapping
    public Result<PageInfo<User>> getUserList(
            @Parameter(description = "搜索关键词 (ID或昵称)") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "账号状态筛选 (0:正常, 1:冻结, 2:已注销)")
            @RequestParam(required = false)
            @Min(value = 0, message = "状态值不能小于0")
            @Max(value = 2, message = "状态值不能大于2") Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getUserList(keyword, status, page, size));
    }

    @Operation(operationId = "freezeUserAdmin", summary = "冻结用户", description = "冻结指定用户账号，冻结后用户无法继续访问系统")
    @PatchMapping("/{id}/freeze")
    public Result<String> freezeUser(
            @Parameter(description = "用户ID", required = true) @PathVariable
            @NotBlank(message = "用户ID不能为空") String id) {
        adminService.freezeUser(id);
        return Result.success("用户已冻结");
    }

    @Operation(operationId = "unfreezeUserAdmin", summary = "解冻用户", description = "解除指定用户的冻结状态，恢复正常登录")
    @PatchMapping("/{id}/unfreeze")
    public Result<String> unfreezeUser(
            @Parameter(description = "用户ID", required = true) @PathVariable
            @NotBlank(message = "用户ID不能为空") String id) {
        adminService.unfreezeUser(id);
        return Result.success("用户已解冻");
    }

    @Operation(operationId = "deleteUserAdmin", summary = "注销用户", description = "根据ID注销用户账号")
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(
            @Parameter(description = "用户ID", required = true) @PathVariable
            @NotBlank(message = "用户ID不能为空") String id) {
        adminService.deleteUser(id);
        return Result.success("用户已注销");
    }
}
