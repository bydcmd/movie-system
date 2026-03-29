package com.movie.backend.controller.admin;

import com.movie.backend.common.Result;
import com.movie.backend.entity.Genre;
import com.movie.backend.service.GenreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Genre Management", description = "管理员电影类型管理接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/admin/genres")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminGenreController {

    @Autowired
    private GenreService genreService;

    @Operation(operationId = "getGenreListAdmin", summary = "获取所有类型（完整对象）", description = "返回所有电影类型的完整信息")
    @GetMapping
    public Result<List<Genre>> getGenreList() {
        return Result.success(genreService.getAllGenres());
    }

    @Operation(operationId = "addGenreAdmin", summary = "新增类型", description = "添加新的电影类型，自动清除缓存")
    @PostMapping
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
    @PutMapping("/{id}")
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
    @DeleteMapping("/{id}")
    public Result<String> deleteGenre(
            @Parameter(description = "类型ID", required = true) @PathVariable
            @Min(value = 1, message = "类型ID必须大于0") Integer id) {
        genreService.deleteGenre(id);
        return Result.success("类型已删除，缓存已清除");
    }
}
