package com.movie.backend.controller.admin;

import com.github.pagehelper.PageInfo;
import com.movie.backend.common.Result;
import com.movie.backend.entity.Comment;
import com.movie.backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

@Tag(name = "Admin Comment Management", description = "管理员评论管理接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/admin/comments")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminCommentController {

    @Autowired
    private AdminService adminService;

    @Operation(operationId = "getCommentListAdmin", summary = "评论列表管理", description = "查看所有用户发布的评论，用于内容审核")
    @GetMapping
    public Result<PageInfo<Comment>> getCommentList(
            @Parameter(description = "关键词 (评论内容)") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getCommentList(keyword, page, size));
    }

    @Operation(operationId = "hideCommentAdmin", summary = "隐藏评论", description = "管理员隐藏指定评论，隐藏后评论不再对外展示")
    @PatchMapping("/{id}/hide")
    public Result<String> hideComment(
            @Parameter(description = "评论ID", required = true) @PathVariable
            @Min(value = 1, message = "评论ID必须大于0") Long id) {
        adminService.hideComment(id);
        return Result.success("评论已隐藏");
    }

    @Operation(operationId = "restoreCommentAdmin", summary = "恢复评论", description = "管理员恢复已隐藏评论的公开展示状态")
    @PatchMapping("/{id}/restore")
    public Result<String> restoreComment(
            @Parameter(description = "评论ID", required = true) @PathVariable
            @Min(value = 1, message = "评论ID必须大于0") Long id) {
        adminService.restoreComment(id);
        return Result.success("评论已恢复");
    }

    @Operation(operationId = "deleteCommentAdmin", summary = "删除评论", description = "管理员强制删除违规评论")
    @DeleteMapping("/{id}")
    public Result<String> deleteComment(
            @Parameter(description = "评论ID", required = true) @PathVariable
            @Min(value = 1, message = "评论ID必须大于0") Long id) {
        adminService.deleteComment(id);
        return Result.success("评论已删除");
    }
}
