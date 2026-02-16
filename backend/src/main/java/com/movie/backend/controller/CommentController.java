package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.common.Result;
import com.movie.backend.dto.CommentVO;
import com.movie.backend.dto.LongReviewDTO;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.User;
import com.movie.backend.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "评论管理", description = "电影评论的发布、查询、点赞及修改")
@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Operation(summary = "获取电影评论列表 (基础版)", description = "分页获取指定电影的评论列表，不包含当前用户的点赞状态")
    @GetMapping("/list")
    public Result<PageInfo<Comment>> getCommentsByMovie(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        return Result.success(commentService.getCommentsByMovieId(movieId, page, size));
    }

    @Operation(summary = "获取电影评论列表 (完整版)", description = "分页获取指定电影的评论列表，包含评论者的用户信息、评分以及当前登录用户的点赞状态")
    @GetMapping("/list-with-rating")
    public Result<PageInfo<CommentVO>> getCommentsWithRatingByMovie(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @CurrentUser(required = false) User user) {
        String userId = user != null ? user.getId() : null;
        return Result.success(commentService.getCommentsWithRatingByMovieId(movieId, userId, page, size));
    }

    @Operation(summary = "获取评论列表 (区分类型)", description = "type=1为短评，type=2为长评")
    @GetMapping("/list-by-type")
    public Result<PageInfo<CommentVO>> getCommentsByType(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "类型: 1-短评, 2-长评", required = true) @RequestParam Integer type,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @CurrentUser(required = false) User user) {
        String userId = user != null ? user.getId() : null;
        return Result.success(commentService.getCommentsByType(movieId, userId, type, page, size));
    }

    @Operation(summary = "发布长评 (图文)", description = "发布带有标题和富文本内容的长评。图片请先调用 /file/upload 接口上传，获取URL后嵌入content中。")
    @PostMapping("/submit-long")
    public Result<String> submitLongReview(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "标题", required = true) @RequestParam String title,
            @Parameter(description = "富文本内容(HTML)", required = true) @RequestParam String content,
            @CurrentUser User user) {
        commentService.submitLongReview(user.getId(), movieId, title, content);
        return Result.success("长评发布成功");
    }

    @Operation(summary = "修改长评", description = "修改已发布的长评标题和富文本内容")
    @PostMapping("/update-long")
    public Result<String> updateLongReview(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "新的标题", required = true) @RequestParam String title,
            @Parameter(description = "新的富文本内容", required = true) @RequestParam String content,
            @CurrentUser User user) {
        commentService.updateLongReview(user.getId(), movieId, title, content);
        return Result.success("长评修改成功");
    }

    @Operation(summary = "发布长评（JSON格式）", description = "发布带有标题和 Tiptap JSON 格式内容的长评。content 字段需要是有效的 Tiptap/ProseMirror JSON 文档格式。")
    @PostMapping("/submit-long-json")
    public Result<String> submitLongReviewJson(
            @Valid @RequestBody LongReviewDTO dto,
            @CurrentUser User user) {
        commentService.submitLongReviewJson(user.getId(), dto);
        return Result.success("长评发布成功");
    }

    @Operation(summary = "修改长评（JSON格式）", description = "修改已发布的长评标题和 Tiptap JSON 格式内容。content 字段需要是有效的 Tiptap/ProseMirror JSON 文档格式。")
    @PostMapping("/update-long-json")
    public Result<String> updateLongReviewJson(
            @Valid @RequestBody LongReviewDTO dto,
            @CurrentUser User user) {
        commentService.updateLongReviewJson(user.getId(), dto);
        return Result.success("长评修改成功");
    }

    @Operation(summary = "发布评论", description = "对指定电影发布评论，每人每部电影仅限一条")
    @PostMapping("/submit")
    public Result<String> submitComment(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "评论内容", required = true) @RequestParam String content,
            @CurrentUser User user) {
        commentService.submitComment(user.getId(), movieId, content);
        return Result.success("评论发布成功");
    }

    @Operation(summary = "获取我的单条评论", description = "获取当前登录用户对某部电影的评论内容")
    @GetMapping("/get")
    public Result<Comment> getUserComment(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @CurrentUser User user) {
        Comment comment = commentService.getUserComment(user.getId(), movieId);
        return Result.success(comment);
    }

    @Operation(summary = "修改评论及评分", description = "同时更新评论内容和评分分值")
    @PostMapping("/update")
    public Result<String> updateComment(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "新的评论内容", required = true) @RequestParam String content,
            @Parameter(description = "新的评分 (1-5)", required = true) @RequestParam Integer rating,
            @CurrentUser User user) {
        commentService.updateCommentWithRating(user.getId(), movieId, content, rating);
        return Result.success("评论和评分已更新");
    }

    @Operation(summary = "仅修改评论内容", description = "只更新文字评论，不改变原有评分")
    @PostMapping("/update-content")
    public Result<String> updateCommentContent(
            @Parameter(description = "电影ID", required = true) @RequestParam Long movieId,
            @Parameter(description = "新的评论内容", required = true) @RequestParam String content,
            @CurrentUser User user) {
        commentService.updateComment(user.getId(), movieId, content);
        return Result.success("评论内容已更新");
    }

    @Operation(summary = "点赞/取消点赞评论", description = "对某条评论进行点赞或取消点赞操作，返回当前是否点赞的状态")
    @PostMapping("/like")
    public Result<Boolean> toggleLike(
            @Parameter(description = "评论ID", required = true) @RequestParam Long commentId,
            @CurrentUser User user) {
        boolean isLiked = commentService.toggleLike(user.getId(), commentId);
        return Result.success(isLiked);
    }

    @Operation(summary = "获取我的评论历史", description = "分页获取当前登录用户发布过的所有评论")
    @GetMapping("/my")
    public Result<PageInfo<Comment>> getMyComments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @CurrentUser User user) {
        return Result.success(commentService.getUserComments(user.getId(), page, size));
    }

    @Operation(summary = "删除评论", description = "用户删除自己发布的评论")
    @PostMapping("/delete")
    public Result<String> deleteComment(
            @Parameter(description = "评论ID", required = true) @RequestParam Long commentId,
            @CurrentUser User user) {
        commentService.deleteComment(user.getId(), commentId);
        return Result.success("评论已删除");
    }

    @Operation(summary = "验证 Tiptap JSON 内容", description = "验证长评内容是否为有效的 Tiptap JSON 格式，不保存到数据库。用于前端发布前的校验。")
    @PostMapping("/validate-json")
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

    @Operation(summary = "解析 Tiptap JSON 内容", description = "将 Tiptap JSON 内容解析为纯文本和摘要信息，不保存到数据库。用于前端预览。")
    @PostMapping("/parse-json")
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
                com.movie.backend.dto.TiptapContentDTO.fromJson(content);
        return Result.success(dto);
    }
}
