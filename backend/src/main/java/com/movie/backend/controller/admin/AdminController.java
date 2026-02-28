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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

import com.movie.backend.dto.AdminMovieDTO;
import com.movie.backend.dto.AdminPersonDTO;
import jakarta.validation.Valid;

@Tag(name = "Admin Management", description = "管理员专属接口，包含数据统计及各类资源的增删改查")
@SecurityRequirement(name = "BearerAuth")
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
    @Operation(operationId = "getDashboardStats", summary = "获取仪表盘统计数据", description = "返回系统总用户数、总电影数等统计指标")
    @GetMapping("/dashboard/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(adminService.getDashboardStats());
    }

    // --- User Management ---
    @Operation(operationId = "getUserListAdmin", summary = "用户列表管理", description = "分页查询所有注册用户")
    @GetMapping("/users")
    public Result<PageInfo<User>> getUserList(
            @Parameter(description = "搜索关键词 (ID或昵称)") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getUserList(keyword, page, size));
    }

    @Operation(operationId = "deleteUserAdmin", summary = "注销用户", description = "根据ID注销用户账号")
    @DeleteMapping("/users/{id}")
    public Result<String> deleteUser(
            @Parameter(description = "用户ID", required = true) @PathVariable
            @NotBlank(message = "用户ID不能为空") String id) {
        adminService.deleteUser(id);
        return Result.success("用户已注销");
    }

    // --- Movie Management ---
    @Operation(operationId = "addMovieAdmin", summary = "新增电影", description = "录入新的电影数据")
    @PostMapping("/movies")
    public Result<String> addMovie(@Valid @RequestBody AdminMovieDTO dto) {
        adminService.addMovie(toMovie(dto));
        return Result.success("电影添加成功");
    }

    @Operation(operationId = "updateMovieAdmin", summary = "更新电影信息", description = "修改已存在的电影数据")
    @PutMapping("/movies/{id}")
    public Result<String> updateMovie(
            @Parameter(description = "电影ID", required = true) @PathVariable
            @Min(value = 1, message = "电影ID必须大于0") Long id,
            @Valid @RequestBody AdminMovieDTO dto) {
        Movie movie = toMovie(dto);
        movie.setId(id);
        adminService.updateMovie(movie);
        return Result.success("电影更新成功");
    }

    @Operation(operationId = "deleteMovieAdmin", summary = "删除电影", description = "根据ID物理删除电影记录")
    @DeleteMapping("/movies/{id}")
    public Result<String> deleteMovie(
            @Parameter(description = "电影ID", required = true) @PathVariable
            @Min(value = 1, message = "电影ID必须大于0") Long id) {
        adminService.deleteMovie(id);
        return Result.success("电影已删除");
    }

    @Operation(operationId = "getMovieListAdmin", summary = "电影列表管理", description = "后台查询电影列表 (无视上下架状态)")
    @GetMapping("/movies")
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
    @Operation(operationId = "getPersonListAdmin", summary = "影人列表管理", description = "分页查询所有演员/导演/编剧")
    @GetMapping("/people")
    public Result<PageInfo<Person>> getPersonList(
            @Parameter(description = "关键词 (中英文名)") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getPersonList(keyword, page, size));
    }

    @Operation(operationId = "addPersonAdmin", summary = "新增影人", description = "录入新的影人资料")
    @PostMapping("/people")
    public Result<String> addPerson(@Valid @RequestBody AdminPersonDTO dto) {
        adminService.addPerson(toPerson(dto));
        return Result.success("影人添加成功");
    }

    @Operation(operationId = "updatePersonAdmin", summary = "更新影人信息", description = "修改影人资料")
    @PutMapping("/people/{id}")
    public Result<String> updatePerson(
            @Parameter(description = "影人ID", required = true) @PathVariable
            @Min(value = 1, message = "影人ID必须大于0") Long id,
            @Valid @RequestBody AdminPersonDTO dto) {
        Person person = toPerson(dto);
        person.setId(id);
        adminService.updatePerson(person);
        return Result.success("影人更新成功");
    }

    private Movie toMovie(AdminMovieDTO dto) {
        Movie movie = new Movie();
        movie.setName(dto.getName());
        movie.setAlias(dto.getAlias());
        movie.setActors(dto.getActors());
        movie.setCover(dto.getCover());
        movie.setDirectors(dto.getDirectors());
        movie.setScore(dto.getScore());
        movie.setDoubanScore(dto.getDoubanScore());
        movie.setVotes(dto.getVotes());
        movie.setDoubanVotes(dto.getDoubanVotes());
        movie.setGenres(dto.getGenres());
        movie.setImdbId(dto.getImdbId());
        movie.setLanguages(dto.getLanguages());
        movie.setMins(dto.getMins());
        movie.setRegions(dto.getRegions());
        movie.setReleaseDate(dto.getReleaseDate());
        movie.setStoryline(dto.getStoryline());
        movie.setYear(dto.getYear());
        movie.setWriters(dto.getWriters());
        movie.setReason(dto.getReason());
        return movie;
    }

    private Person toPerson(AdminPersonDTO dto) {
        Person person = new Person();
        person.setName(dto.getName());
        person.setSex(dto.getSex());
        person.setNameEn(dto.getNameEn());
        person.setNameZh(dto.getNameZh());
        person.setBirth(dto.getBirth());
        person.setBirthplace(dto.getBirthplace());
        person.setProfession(dto.getProfession());
        person.setBiography(dto.getBiography());
        person.setAvatar(dto.getAvatar());
        return person;
    }

    @Operation(operationId = "deletePersonAdmin", summary = "删除影人", description = "删除影人记录")
    @DeleteMapping("/people/{id}")
    public Result<String> deletePerson(
            @Parameter(description = "影人ID", required = true) @PathVariable
            @Min(value = 1, message = "影人ID必须大于0") Long id) {
        adminService.deletePerson(id);
        return Result.success("影人已删除");
    }

    // --- Comment Management ---
    @Operation(operationId = "getCommentListAdmin", summary = "评论列表管理", description = "查看所有用户发布的评论，用于内容审核")
    @GetMapping("/comments")
    public Result<PageInfo<Comment>> getCommentList(
            @Parameter(description = "关键词 (评论内容)") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getCommentList(keyword, page, size));
    }

    @Operation(operationId = "deleteCommentAdmin", summary = "删除评论", description = "管理员强制删除违规评论")
    @DeleteMapping("/comments/{id}")
    public Result<String> deleteComment(
            @Parameter(description = "评论ID", required = true) @PathVariable
            @Min(value = 1, message = "评论ID必须大于0") Long id) {
        adminService.deleteComment(id);
        return Result.success("评论已删除");
    }

    // --- Genre Management (with Cache Eviction) ---
    @Operation(operationId = "getGenreListAdmin", summary = "获取所有类型（完整对象）", description = "返回所有电影类型的完整信息")
    @GetMapping("/genres")
    public Result<List<Genre>> getGenreList() {
        return Result.success(genreService.getAllGenres());
    }

    @Operation(operationId = "addGenreAdmin", summary = "新增类型", description = "添加新的电影类型，自动清除缓存")
    @PostMapping("/genres")
    public Result<String> addGenre(@RequestBody Genre genre) {
        if (genre == null) {
            return Result.fail(400, "类型信息不能为空");
        }
        if (genre.getName() == null || genre.getName().trim().isEmpty()) {
            return Result.fail(400, "类型名称不能为空");
        }
        genreService.addGenre(genre);
        return Result.success("类型添加成功，缓存已清除");
    }

    @Operation(operationId = "updateGenreAdmin", summary = "更新类型", description = "修改电影类型信息，自动清除缓存")
    @PutMapping("/genres/{id}")
    public Result<String> updateGenre(
            @Parameter(description = "类型ID", required = true) @PathVariable
            @Min(value = 1, message = "类型ID必须大于0") Integer id,
            @RequestBody Genre genre) {
        if (genre == null) {
            return Result.fail(400, "类型信息不能为空");
        }

        Genre existing = genreService.getGenreById(id);
        if (existing == null) {
            return Result.notFound("类型不存在");
        }

        if (genre.getName() != null && genre.getName().trim().isEmpty()) {
            return Result.fail(400, "类型名称不能为空");
        }

        Genre toUpdate = new Genre();
        toUpdate.setId(id);
        toUpdate.setName(genre.getName() != null ? genre.getName().trim() : existing.getName());
        toUpdate.setNameEn(genre.getNameEn() != null ? genre.getNameEn().trim() : existing.getNameEn());
        toUpdate.setDescription(genre.getDescription() != null ? genre.getDescription().trim() : existing.getDescription());

        genreService.updateGenre(toUpdate);
        return Result.success("类型更新成功，缓存已清除");
    }

    @Operation(operationId = "deleteGenreAdmin", summary = "删除类型", description = "删除电影类型，自动清除缓存")
    @DeleteMapping("/genres/{id}")
    public Result<String> deleteGenre(
            @Parameter(description = "类型ID", required = true) @PathVariable
            @Min(value = 1, message = "类型ID必须大于0") Integer id) {
        genreService.deleteGenre(id);
        return Result.success("类型已删除，缓存已清除");
    }

    // --- Region Management (with Cache Eviction) ---
    @Operation(operationId = "getRegionListAdmin", summary = "获取所有地区（完整对象）", description = "返回所有电影地区的完整信息")
    @GetMapping("/regions")
    public Result<List<Region>> getRegionList() {
        return Result.success(regionService.getAllRegions());
    }

    @Operation(operationId = "addRegionAdmin", summary = "新增地区", description = "添加新的电影地区，自动清除缓存")
    @PostMapping("/regions")
    public Result<String> addRegion(@RequestBody Region region) {
        if (region == null) {
            return Result.fail(400, "地区信息不能为空");
        }
        if (region.getName() == null || region.getName().trim().isEmpty()) {
            return Result.fail(400, "地区名称不能为空");
        }
        regionService.addRegion(region);
        return Result.success("地区添加成功，缓存已清除");
    }

    @Operation(operationId = "updateRegionAdmin", summary = "更新地区", description = "修改电影地区信息，自动清除缓存")
    @PutMapping("/regions/{id}")
    public Result<String> updateRegion(
            @Parameter(description = "地区ID", required = true) @PathVariable
            @Min(value = 1, message = "地区ID必须大于0") Integer id,
            @RequestBody Region region) {
        if (region == null) {
            return Result.fail(400, "地区信息不能为空");
        }

        Region existing = regionService.getRegionById(id);
        if (existing == null) {
            return Result.notFound("地区不存在");
        }

        if (region.getName() != null && region.getName().trim().isEmpty()) {
            return Result.fail(400, "地区名称不能为空");
        }

        Region toUpdate = new Region();
        toUpdate.setId(id);
        toUpdate.setName(region.getName() != null ? region.getName().trim() : existing.getName());
        toUpdate.setNameEn(region.getNameEn() != null ? region.getNameEn().trim() : existing.getNameEn());
        toUpdate.setDescription(region.getDescription() != null ? region.getDescription().trim() : existing.getDescription());

        regionService.updateRegion(toUpdate);
        return Result.success("地区更新成功，缓存已清除");
    }

    @Operation(operationId = "deleteRegionAdmin", summary = "删除地区", description = "删除电影地区，自动清除缓存")
    @DeleteMapping("/regions/{id}")
    public Result<String> deleteRegion(
            @Parameter(description = "地区ID", required = true) @PathVariable
            @Min(value = 1, message = "地区ID必须大于0") Integer id) {
        regionService.deleteRegion(id);
        return Result.success("地区已删除，缓存已清除");
    }

    @Operation(operationId = "refreshPersonalizedRecommendationsByUserAdmin", summary = "刷新指定用户猜你喜欢缓存", description = "清除指定用户的猜你喜欢缓存，下一次请求将回源刷新")
    @DeleteMapping("/recommendations/personalized/cache/{targetUserId}")
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
    @DeleteMapping("/recommendations/personalized/cache/all")
    public Result<String> refreshAllPersonalizedRecommendations(
            @Parameter(description = "确认清除全部用户缓存，必须传 true") @RequestParam(defaultValue = "false") boolean confirmAll) {
        if (!confirmAll) {
            return Result.badRequest("危险操作，请传 confirmAll=true 后重试");
        }

        analyticsService.evictPersonalizedCache(null);
        return Result.success("已刷新猜你喜欢全量缓存");
    }
}
