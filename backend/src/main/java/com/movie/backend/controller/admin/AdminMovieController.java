package com.movie.backend.controller.admin;

import com.github.pagehelper.PageInfo;
import com.movie.backend.common.Result;
import com.movie.backend.dto.AdminMovieDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Movie Management", description = "管理员电影管理接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/admin/movies")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminMovieController {

    @Autowired
    private AdminService adminService;

    @Operation(operationId = "addMovieAdmin", summary = "新增电影", description = "录入新的电影数据")
    @PostMapping
    public Result<String> addMovie(@Valid @RequestBody AdminMovieDTO dto) {
        adminService.addMovie(toMovie(dto));
        return Result.success("电影添加成功");
    }

    @Operation(operationId = "updateMovieAdmin", summary = "更新电影信息", description = "修改已存在的电影数据")
    @PutMapping("/{id}")
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
    @DeleteMapping("/{id}")
    public Result<String> deleteMovie(
            @Parameter(description = "电影ID", required = true) @PathVariable
            @Min(value = 1, message = "电影ID必须大于0") Long id) {
        adminService.deleteMovie(id);
        return Result.success("电影已删除");
    }

    @Operation(operationId = "getMovieListAdmin", summary = "电影列表管理", description = "后台查询电影列表 (无视上下架状态)")
    @GetMapping
    public Result<PageInfo<Movie>> getMovieList(
            @Parameter(description = "关键词") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量至少为1")
            @Max(value = 100, message = "每页数量最多为100") int size) {
        return Result.success(adminService.getMovieList(keyword, page, size));
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
}
