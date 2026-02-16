package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import com.movie.backend.annotation.CurrentUser;
import com.movie.backend.common.Result;
import com.movie.backend.dto.MyRatingVO;
import com.movie.backend.entity.Rating;
import com.movie.backend.entity.User;
import com.movie.backend.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Rating Management")
@RestController
@RequestMapping("/rating")
@Validated
public class RatingController {

    @Autowired
    private RatingService ratingService;

    @Operation(summary = "Submit Rating")
    @PostMapping("/submit")
    public Result<String> submitRating(
            @RequestParam
            @NotNull(message = "电影ID不能为空")
            @Min(value = 1, message = "电影ID必须大于0") Long movieId,
            @RequestParam
            @NotNull(message = "评分不能为空")
            @Min(value = 1, message = "评分必须在 1 到 5 之间")
            @Max(value = 5, message = "评分必须在 1 到 5 之间") Integer rating,
            @CurrentUser User user) {
        ratingService.submitRating(user.getId(), movieId, rating);
        return Result.success("Rating Submitted");
    }

    @Operation(summary = "Update Rating")
    @PostMapping("/update")
    public Result<String> updateRating(
            @RequestParam
            @NotNull(message = "电影ID不能为空")
            @Min(value = 1, message = "电影ID必须大于0") Long movieId,
            @RequestParam
            @NotNull(message = "评分不能为空")
            @Min(value = 1, message = "评分必须在 1 到 5 之间")
            @Max(value = 5, message = "评分必须在 1 到 5 之间") Integer rating,
            @CurrentUser User user) {
        ratingService.updateRating(user.getId(), movieId, rating);
        return Result.success("Rating Updated");
    }

    @Operation(summary = "Get User Rating")
    @GetMapping("/get")
    public Result<Rating> getUserRating(
            @RequestParam
            @NotNull(message = "电影ID不能为空")
            @Min(value = 1, message = "电影ID必须大于0") Long movieId,
            @CurrentUser User user) {
        Rating rating = ratingService.getUserRating(user.getId(), movieId);
        return Result.success(rating);
    }

    @Operation(summary = "Get My Ratings")
    @GetMapping("/my")
    public Result<PageInfo<MyRatingVO>> getMyRatings(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size,
            @CurrentUser User user) {
        return Result.success(ratingService.getMyRatingVOList(user.getId(), page, size));
    }

    @Operation(summary = "Clear All My Ratings")
    @DeleteMapping("/clear")
    public Result<String> clearMyRatings(@CurrentUser User user) {
        ratingService.clearUserRatings(user.getId());
        return Result.success("All ratings cleared");
    }

    @Operation(summary = "Batch Delete Ratings")
    @DeleteMapping("/batch")
    public Result<String> deleteRatingsBatch(
            @RequestBody
            @NotEmpty(message = "电影ID列表不能为空") List<Long> movieIds,
            @CurrentUser User user) {
        ratingService.deleteRatingsBatch(user.getId(), movieIds);
        return Result.success("Selected ratings deleted");
    }
}
