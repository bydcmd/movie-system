package com.movie.backend.controller.admin;

import com.movie.backend.common.Result;
import com.movie.backend.entity.Region;
import com.movie.backend.service.RegionService;
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

@Tag(name = "Admin Region Management", description = "管理员电影地区管理接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/admin/regions")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminRegionController {

    @Autowired
    private RegionService regionService;

    @Operation(operationId = "getRegionListAdmin", summary = "获取所有地区（完整对象）", description = "返回所有电影地区的完整信息")
    @GetMapping
    public Result<List<Region>> getRegionList() {
        return Result.success(regionService.getAllRegions());
    }

    @Operation(operationId = "addRegionAdmin", summary = "新增地区", description = "添加新的电影地区，自动清除缓存")
    @PostMapping
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
    @PutMapping("/{id}")
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
    @DeleteMapping("/{id}")
    public Result<String> deleteRegion(
            @Parameter(description = "地区ID", required = true) @PathVariable
            @Min(value = 1, message = "地区ID必须大于0") Integer id) {
        regionService.deleteRegion(id);
        return Result.success("地区已删除，缓存已清除");
    }
}
