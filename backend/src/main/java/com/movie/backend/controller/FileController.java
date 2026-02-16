package com.movie.backend.controller;

import com.movie.backend.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Tag(name = "文件管理", description = "图片上传及静态资源访问")
@Slf4j
@RestController
@RequestMapping("/common")
public class FileController {

    @Value("${file.upload-path:./uploaded/}")
    private String uploadPath;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Operation(summary = "上传图片", description = "支持 JPG/PNG/GIF/WEBP 格式，最大 5MB。返回相对路径 URL。")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> upload(
            @Parameter(description = "选择图片文件", required = true)
            @RequestPart("file") MultipartFile file) { // 使用 @RequestPart 配合 Swagger 效果更好

        if (file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.fail(400, "文件大小不能超过 5MB");
        }

        // ...原有逻辑保持不变...
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            return Result.fail(400, "不支持的文件格式，仅允许: " + ALLOWED_EXTENSIONS);
        }

        String fileName = UUID.randomUUID() + suffix;
        File dir = new File(uploadPath);
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.fail(500, "无法创建上传目录");
        }

        try {
            file.transferTo(new File(dir, fileName));
            String fileUrl = "/images/" + fileName;
            log.info("文件上传成功: {} -> {}", originalFilename, fileUrl);
            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("文件上传失败: {}", originalFilename, e);
            return Result.fail(500, "文件上传失败: " + e.getMessage());
        }
    }
}