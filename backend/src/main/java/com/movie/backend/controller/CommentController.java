package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.movie.backend.common.Result;
import com.movie.backend.dto.BatchIdsDTO;
import com.movie.backend.dto.CommentVO;
import com.movie.backend.dto.LongReviewDTO;
import com.movie.backend.dto.UpdateCommentContentDTO;
import com.movie.backend.dto.UpdateCommentWithRatingDTO;
import com.movie.backend.dto.UpdateLongReviewDTO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.User;
import com.movie.backend.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "Comment Management", description = "电影评论的发布、查询、点赞及修改")
@RestController
@Validated
public class CommentController {

    private static final int GUEST_COMMENT_LIMIT = 20;

    @Autowired
    private CommentService commentService;

    @Operation(operationId = "getMovieComments", summary = "获取电影评论列表", description = "分页获取指定电影评论。可选按 type 筛选：1-短评，2-长评。可选登录（游客最多查看20条）。")
    @GetMapping("/movies/{movieId}/comments")
    public Result<PageInfo<CommentVO>> getMovieComments(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @Parameter(description = "评论类型: 1-短评, 2-长评") @RequestParam(required = false) Integer type,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal User user) {
        validateGuestPageWindow(page, size, user == null);
        String userId = user != null ? user.getId() : null;
        if (type != null) {
            if (type != 1 && type != 2) {
                throw new IllegalArgumentException("type 只能为 1(短评) 或 2(长评)");
            }
            return Result.success(commentService.getCommentsByType(movieId, userId, type, page, size));
        }
        return Result.success(commentService.getCommentsWithRatingByMovieId(movieId, userId, page, size));
    }

    @Operation(operationId = "submitMovieLongReview", summary = "发布长评", description = "对指定电影发布长评。content 字段需为有效的 Tiptap/ProseMirror JSON 文档格式。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/movies/{movieId}/long-reviews")
    public Result<String> submitMovieLongReview(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @Valid @RequestBody UpdateLongReviewDTO dto,
            @AuthenticationPrincipal User user) {
        LongReviewDTO longReview = new LongReviewDTO();
        longReview.setMovieId(movieId);
        longReview.setTitle(dto.getTitle());
        longReview.setContent(dto.getContent());
        commentService.submitLongReview(user.getId(), longReview);
        return Result.success("长评发布成功");
    }

    @Operation(operationId = "updateMyMovieLongReview", summary = "修改我的长评", description = "修改我在指定电影下的长评。content 字段需为有效的 Tiptap/ProseMirror JSON 文档格式。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/movies/{movieId}/long-reviews/me")
    public Result<String> updateMyMovieLongReview(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @Valid @RequestBody UpdateLongReviewDTO dto,
            @AuthenticationPrincipal User user) {
        commentService.updateLongReview(user.getId(), movieId, dto.getTitle(), dto.getContent());
        return Result.success("长评修改成功");
    }

    @Operation(operationId = "getMyMovieLongReview", summary = "获取我的长评", description = "获取当前登录用户对指定电影的可编辑长评内容；如存在草稿则优先返回草稿，否则返回已发布长评。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/movies/{movieId}/long-reviews/me")
    public Result<Comment> getMyMovieLongReview(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @AuthenticationPrincipal User user) {
        Comment comment = commentService.getUserLongReview(user.getId(), movieId);
        return Result.success(comment);
    }

    @Operation(operationId = "getMovieLongReviewDetail", summary = "获取长评详情", description = "根据电影ID和评论ID获取公开长评详情，返回正文、作者信息、点赞状态和作者评分。")
    @GetMapping("/movies/{movieId}/long-reviews/{commentId}")
    public Result<CommentVO> getMovieLongReviewDetail(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @Parameter(description = "评论ID", required = true) @PathVariable @NotNull @Min(1) Long commentId,
            @AuthenticationPrincipal User user) {
        String currentUserId = user != null ? user.getId() : null;
        CommentVO review = commentService.getMovieLongReviewDetail(movieId, commentId, currentUserId);
        return Result.success(review);
    }

    @Operation(operationId = "submitMovieComment", summary = "发布短评", description = "对指定电影发布短评，每人每部电影仅限一条。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/movies/{movieId}/comments")
    public Result<String> submitMovieComment(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @Valid @RequestBody UpdateCommentContentDTO dto,
            @AuthenticationPrincipal User user) {
        commentService.submitComment(user.getId(), movieId, dto.getContent());
        return Result.success("评论发布成功");
    }

    @Operation(operationId = "getMyMovieComment", summary = "获取我的短评", description = "获取当前登录用户对指定电影发布的短评内容。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/movies/{movieId}/comments/me")
    public Result<Comment> getMyMovieComment(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @AuthenticationPrincipal User user) {
        Comment comment = commentService.getUserShortComment(user.getId(), movieId);
        return Result.success(comment);
    }

    @Operation(operationId = "updateMyMovieCommentWithRating", summary = "修改我的评论及评分", description = "同时更新我在指定电影下的短评内容和评分分值。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/movies/{movieId}/comments/me")
    public Result<String> updateMyMovieCommentWithRating(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @Valid @RequestBody UpdateCommentWithRatingDTO dto,
            @AuthenticationPrincipal User user) {
        commentService.updateCommentWithRating(user.getId(), movieId, dto.getContent(), dto.getRating());
        return Result.success("评论和评分已更新");
    }

    @Operation(operationId = "updateMyMovieCommentContent", summary = "仅修改我的评论内容", description = "只更新我在指定电影下的短评内容，不改变原有评分。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/movies/{movieId}/comments/me")
    public Result<String> updateMyMovieCommentContent(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @Valid @RequestBody UpdateCommentContentDTO dto,
            @AuthenticationPrincipal User user) {
        commentService.updateComment(user.getId(), movieId, dto.getContent());
        return Result.success("评论内容已更新");
    }

    @Operation(operationId = "likeComment", summary = "点赞评论", description = "将评论点赞状态设置为已点赞（幂等）")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/comments/{commentId}/like")
    public Result<Boolean> likeComment(
            @Parameter(description = "评论ID", required = true) @PathVariable @Min(1) Long commentId,
            @AuthenticationPrincipal User user) {
        return Result.success(commentService.likeComment(user.getId(), commentId));
    }

    @Operation(operationId = "unlikeComment", summary = "取消点赞评论", description = "将评论点赞状态设置为未点赞（幂等）")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/comments/{commentId}/like")
    public Result<Boolean> unlikeComment(
            @Parameter(description = "评论ID", required = true) @PathVariable @Min(1) Long commentId,
            @AuthenticationPrincipal User user) {
        return Result.success(commentService.unlikeComment(user.getId(), commentId));
    }

    @Operation(operationId = "getMyComments", summary = "获取我的评论历史", description = "分页获取当前登录用户发布过的所有评论")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comments/me/history")
    public Result<PageInfo<Comment>> getMyComments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal User user) {
        return Result.success(commentService.getUserComments(user.getId(), page, size));
    }

    @Operation(operationId = "deleteMyComments", summary = "删除评论", description = "删除自己发布的评论，支持单个或批量删除。传入单个ID即删除单条，传入多个ID即批量删除。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/comments")
    public Result<String> deleteComments(
            @Valid @RequestBody BatchIdsDTO dto,
            @AuthenticationPrincipal User user) {
        int deletedCount = commentService.deleteComments(user.getId(), dto.getIds());
        return Result.success("成功删除 " + deletedCount + " 条评论");
    }

    @Operation(operationId = "validateJsonContent", summary = "验证 Tiptap JSON 内容", description = "验证长评内容是否为有效的 Tiptap JSON 格式，不保存到数据库。用于前端发布前的校验。")
    @PostMapping("/comments/json/validate")
    public Result<com.movie.backend.utils.TiptapJsonValidator.ValidationResult> validateJsonContent(
            @Parameter(description = "Tiptap JSON 内容", required = true) @RequestBody String content) {
        com.movie.backend.utils.TiptapJsonValidator.ValidationResult result = 
                com.movie.backend.utils.TiptapJsonValidator.validate(content);
        if (result.isValid()) {
            return Result.success(result);
        } else {
            return Result.fail(400, result.getMessage());
        }
    }

    @Operation(operationId = "parseJsonContent", summary = "解析 Tiptap JSON 内容", description = "将 Tiptap JSON 内容解析为纯文本和摘要信息，不保存到数据库。用于前端预览。")
    @PostMapping("/comments/json/parse")
    public Result<com.movie.backend.dto.TiptapContentDTO> parseJsonContent(
            @Parameter(description = "Tiptap JSON 内容", required = true) @RequestBody String content) {
        // 先验证格式
        com.movie.backend.utils.TiptapJsonValidator.ValidationResult validationResult = 
                com.movie.backend.utils.TiptapJsonValidator.validate(content);
        if (!validationResult.isValid()) {
            return Result.fail(400, validationResult.getMessage());
        }
        // 解析内容
        com.movie.backend.dto.TiptapContentDTO dto = 
                com.movie.backend.utils.TiptapContentConverter.toDto(content);
        return Result.success(dto);
    }

    @Operation(operationId = "saveLongReviewDraft", summary = "保存长评草稿", description = "保存长评草稿，内容可以为空。如已有草稿则更新；即使已发布过长评，也允许保留一份独立草稿。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/movies/{movieId}/long-reviews/draft")
    public Result<String> saveLongReviewDraft(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @RequestBody UpdateLongReviewDTO dto,
            @AuthenticationPrincipal User user) {
        commentService.saveLongReviewDraft(user.getId(), movieId, dto.getTitle(), dto.getContent());
        return Result.success("草稿保存成功");
    }

    @Operation(operationId = "updateLongReviewDraft", summary = "更新长评草稿", description = "更新已有的长评草稿内容。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/movies/{movieId}/long-reviews/draft")
    public Result<String> updateLongReviewDraft(
            @Parameter(description = "电影ID", required = true) @PathVariable @NotNull @Min(1) Long movieId,
            @RequestBody UpdateLongReviewDTO dto,
            @AuthenticationPrincipal User user) {
        commentService.updateLongReviewDraft(user.getId(), movieId, dto.getTitle(), dto.getContent());
        return Result.success("草稿更新成功");
    }

    @Operation(operationId = "publishDraft", summary = "发布草稿", description = "将长评草稿发布为正式评论；如已存在正式长评，则用草稿内容覆盖正式长评。")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/long-reviews/{commentId}/publish")
    public Result<String> publishDraft(
            @Parameter(description = "评论ID", required = true) @PathVariable @NotNull @Min(1) Long commentId,
            @AuthenticationPrincipal User user) {
        commentService.publishDraft(user.getId(), commentId);
        return Result.success("草稿发布成功");
    }

    private void validateGuestPageWindow(int page, int size, boolean guest) {
        if (!guest) {
            return;
        }
        long visibleCount = (long) (page - 1) * size + size;
        if (visibleCount > GUEST_COMMENT_LIMIT) {
            throw new IllegalArgumentException("游客最多查看前20条评论");
        }
    }
}
