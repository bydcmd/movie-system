package com.movie.backend.controller.admin;

import com.github.pagehelper.PageInfo;
import com.movie.backend.common.Result;
import com.movie.backend.dto.AdminPersonDTO;
import com.movie.backend.entity.Person;
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

@Tag(name = "Admin Person Management", description = "管理员影人管理接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/admin/people")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminPersonController {

    @Autowired
    private AdminService adminService;

    @Operation(operationId = "getPersonListAdmin", summary = "影人列表管理", description = "分页查询所有演员/导演/编剧")
    @GetMapping
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
    @PostMapping
    public Result<String> addPerson(@Valid @RequestBody AdminPersonDTO dto) {
        adminService.addPerson(toPerson(dto));
        return Result.success("影人添加成功");
    }

    @Operation(operationId = "updatePersonAdmin", summary = "更新影人信息", description = "修改影人资料")
    @PutMapping("/{id}")
    public Result<String> updatePerson(
            @Parameter(description = "影人ID", required = true) @PathVariable
            @Min(value = 1, message = "影人ID必须大于0") Long id,
            @Valid @RequestBody AdminPersonDTO dto) {
        Person person = toPerson(dto);
        person.setId(id);
        adminService.updatePerson(person);
        return Result.success("影人更新成功");
    }

    @Operation(operationId = "deletePersonAdmin", summary = "删除影人", description = "删除影人记录")
    @DeleteMapping("/{id}")
    public Result<String> deletePerson(
            @Parameter(description = "影人ID", required = true) @PathVariable
            @Min(value = 1, message = "影人ID必须大于0") Long id) {
        adminService.deletePerson(id);
        return Result.success("影人已删除");
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
}
