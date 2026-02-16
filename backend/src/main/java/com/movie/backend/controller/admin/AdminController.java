package com.movie.backend.controller.admin;

import com.github.pagehelper.PageInfo;
import com.movie.backend.common.Result;
import com.movie.backend.entity.Comment;
import com.movie.backend.entity.Genre;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.entity.Region;
import com.movie.backend.entity.User;
import com.movie.backend.service.AdminService;
import com.movie.backend.service.AnalyticsService;
import com.movie.backend.service.GenreService;
import com.movie.backend.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Tag(name = "后台管理", description = "管理员专属接口，包含数据统计及各类资源的增删改查")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private GenreService genreService;

    @Autowired
    private RegionService regionService;

    @Autowired
    private AnalyticsService analyticsService;

    // --- Dashboard ---
    @Operation(summary = "获取仪表盘统计数据", description = "返回系统总用户数、总电影数等统计指标")
    @GetMapping("/dashboard/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(adminService.getDashboardStats());
    }

    // --- User Management ---
    @Operation(summary = "用户列表管理", description = "分页查询所有注册用户")
    @GetMapping("/user/list")
    public Result<PageInfo<User>> getUserList(
            @Parameter(description = "搜索关键词 (ID或昵称)") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getUserList(keyword, page, size));
    }

    @Operation(summary = "注销用户", description = "根据ID注销用户账号")
    @DeleteMapping("/user/delete/{id}")
    public Result<String> deleteUser(
            @Parameter(description = "用户ID", required = true) @PathVariable
            @NotBlank(message = "用户ID不能为空") String id) {
        adminService.deleteUser(id);
        return Result.success("用户已注销");
    }

    // --- Movie Management ---
    @Operation(summary = "新增电影", description = "录入新的电影数据")
    @PostMapping("/movie/add")
    public Result<String> addMovie(@RequestBody Movie movie) {
        adminService.addMovie(movie);
        return Result.success("电影添加成功");
    }

    @Operation(summary = "更新电影信息", description = "修改已存在的电影数据")
    @PutMapping("/movie/update")
    public Result<String> updateMovie(@RequestBody Movie movie) {
        adminService.updateMovie(movie);
        return Result.success("电影更新成功");
    }

    @Operation(summary = "删除电影", description = "根据ID物理删除电影记录")
    @DeleteMapping("/movie/delete/{id}")
    public Result<String> deleteMovie(
            @Parameter(description = "电影ID", required = true) @PathVariable
            @Min(value = 1, message = "电影ID必须大于0") Long id) {
        adminService.deleteMovie(id);
        return Result.success("电影已删除");
    }

    @Operation(summary = "电影列表管理", description = "后台查询电影列表 (无视上下架状态)")
    @GetMapping("/movie/list")
    public Result<PageInfo<Movie>> getMovieList(
            @Parameter(description = "关键词") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getMovieList(keyword, page, size));
    }

    // --- Person Management ---
    @Operation(summary = "影人列表管理", description = "分页查询所有演员/导演/编剧")
    @GetMapping("/person/list")
    public Result<PageInfo<Person>> getPersonList(
            @Parameter(description = "关键词 (中英文名)") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getPersonList(keyword, page, size));
    }

    @Operation(summary = "新增影人", description = "录入新的影人资料")
    @PostMapping("/person/add")
    public Result<String> addPerson(@RequestBody Person person) {
        adminService.addPerson(person);
        return Result.success("影人添加成功");
    }

    @Operation(summary = "更新影人信息", description = "修改影人资料")
    @PutMapping("/person/update")
    public Result<String> updatePerson(@RequestBody Person person) {
        adminService.updatePerson(person);
        return Result.success("影人更新成功");
    }

    @Operation(summary = "删除影人", description = "删除影人记录")
    @DeleteMapping("/person/delete/{id}")
    public Result<String> deletePerson(
            @Parameter(description = "影人ID", required = true) @PathVariable
            @Min(value = 1, message = "影人ID必须大于0") Long id) {
        adminService.deletePerson(id);
        return Result.success("影人已删除");
    }

    // --- Comment Management ---
    @Operation(summary = "评论列表管理", description = "查看所有用户发布的评论，用于内容审核")
    @GetMapping("/comment/list")
    public Result<PageInfo<Comment>> getCommentList(
            @Parameter(description = "关键词 (评论内容)") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getCommentList(keyword, page, size));
    }

    @Operation(summary = "删除评论", description = "管理员强制删除违规评论")
    @DeleteMapping("/comment/delete/{id}")
    public Result<String> deleteComment(
            @Parameter(description = "评论ID", required = true) @PathVariable
            @Min(value = 1, message = "评论ID必须大于0") Long id) {
        adminService.deleteComment(id);
        return Result.success("评论已删除");
    }

    // --- Genre Management (with Cache Eviction) ---
    @Operation(summary = "获取所有类型（完整对象）", description = "返回所有电影类型的完整信息")
    @GetMapping("/genre/list")
    public Result<List<Genre>> getGenreList() {
        return Result.success(genreService.getAllGenres());
    }

    @Operation(summary = "新增类型", description = "添加新的电影类型，自动清除缓存")
    @PostMapping("/genre/add")
    public Result<String> addGenre(@RequestBody Genre genre) {
        genreService.addGenre(genre);
        return Result.success("类型添加成功，缓存已清除");
    }

    @Operation(summary = "更新类型", description = "修改电影类型信息，自动清除缓存")
    @PutMapping("/genre/update")
    public Result<String> updateGenre(@RequestBody Genre genre) {
        genreService.updateGenre(genre);
        return Result.success("类型更新成功，缓存已清除");
    }

    @Operation(summary = "删除类型", description = "删除电影类型，自动清除缓存")
    @DeleteMapping("/genre/delete/{id}")
    public Result<String> deleteGenre(
            @Parameter(description = "类型ID", required = true) @PathVariable
            @Min(value = 1, message = "类型ID必须大于0") Integer id) {
        genreService.deleteGenre(id);
        return Result.success("类型已删除，缓存已清除");
    }

    // --- Region Management (with Cache Eviction) ---
    @Operation(summary = "获取所有地区（完整对象）", description = "返回所有电影地区的完整信息")
    @GetMapping("/region/list")
    public Result<List<Region>> getRegionList() {
        return Result.success(regionService.getAllRegions());
    }

    @Operation(summary = "新增地区", description = "添加新的电影地区，自动清除缓存")
    @PostMapping("/region/add")
    public Result<String> addRegion(@RequestBody Region region) {
        regionService.addRegion(region);
        return Result.success("地区添加成功，缓存已清除");
    }

    @Operation(summary = "更新地区", description = "修改电影地区信息，自动清除缓存")
    @PutMapping("/region/update")
    public Result<String> updateRegion(@RequestBody Region region) {
        regionService.updateRegion(region);
        return Result.success("地区更新成功，缓存已清除");
    }

    @Operation(summary = "删除地区", description = "删除电影地区，自动清除缓存")
    @DeleteMapping("/region/delete/{id}")
    public Result<String> deleteRegion(
            @Parameter(description = "地区ID", required = true) @PathVariable
            @Min(value = 1, message = "地区ID必须大于0") Integer id) {
        regionService.deleteRegion(id);
        return Result.success("地区已删除，缓存已清除");
    }

    @Operation(summary = "刷新猜你喜欢缓存", description = "清除指定用户或全部用户的猜你喜欢缓存，下一次请求将回源刷新")
    @PostMapping("/recommendations/personalized/refresh")
    public Result<String> refreshPersonalizedRecommendations(
            @Parameter(description = "用户ID，不传则清除全部用户缓存") @RequestParam(required = false) String userId) {
        analyticsService.evictPersonalizedCache(userId);
        if (userId == null || userId.trim().isEmpty()) {
            return Result.success("已刷新猜你喜欢全量缓存");
        }
        return Result.success("已刷新用户猜你喜欢缓存: " + userId);
    }
}
