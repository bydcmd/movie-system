package com.movie.backend.controller;

import com.github.pagehelper.PageInfo;
import com.movie.backend.common.Result;
import com.movie.backend.entity.Movie;
import com.movie.backend.entity.Person;
import com.movie.backend.service.PersonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "影人库", description = "查询演员、导演、编剧的详细资料")
@RestController
@RequestMapping("/person")
public class PersonController {

    @Autowired
    private PersonService personService;

    @Operation(summary = "获取影人详情", description = "包含生平简介、出生地、职业等详细信息")
    @GetMapping("/detail/{id}")
    public Result<Person> getDetail(
            @Parameter(description = "影人ID", required = true, example = "1054521")
            @PathVariable Long id) {
        Person person = personService.getDetail(id);
        if (person == null) {
            return Result.fail(404, "影人不存在");
        }
        return Result.success(person);
    }

    @Operation(summary = "搜索影人列表", description = "支持按中英文名模糊搜索，分页返回")
    @GetMapping("/list")
    public Result<PageInfo<Person>> getList(
            @Parameter(description = "关键词") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        return Result.success(personService.getList(keyword, page, size));
    }

    @Operation(summary = "获取影人相关电影", description = "查找该影人参与（导演或出演）的所有电影")
    @GetMapping("/{id}/movies")
    public Result<List<Movie>> getMovies(
            @Parameter(description = "影人ID", required = true) @PathVariable Long id) {
        return Result.success(personService.getMovies(id));
    }
}
