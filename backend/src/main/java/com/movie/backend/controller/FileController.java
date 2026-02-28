package com.movie.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.movie.backend.common.Result;
import com.movie.backend.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@Tag(name = "File Management", description = "图片上传及静态资源访问")
@Slf4j
@RestController
@RequestMapping("/files")
@PreAuthorize("isAuthenticated()")
public class FileController {

    @Value("${file.upload-path:./uploaded/}")
    private String uploadPath;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Operation(operationId = "uploadImage", summary = "上传图片", description = "支持 JPG/PNG/GIF/WEBP 格式，最大 5MB。返回相对路径 URL。")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> upload(
            @Parameter(description = "选择图片文件", required = true)
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User user) { // 使用 @RequestPart 配合 Swagger 效果更好

        if (file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.fail(400, "文件大小不能超过 5MB");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            return Result.fail(400, "不支持的文件格式，仅允许: " + ALLOWED_EXTENSIONS);
        }

        String contentType = file.getContentType();
        if (contentType != null && ALLOWED_CONTENT_TYPES.stream().noneMatch(type -> type.equalsIgnoreCase(contentType))) {
            return Result.fail(400, "不支持的文件类型");
        }

        if (!hasValidImageSignature(file, suffix)) {
            return Result.fail(400, "文件内容与扩展名不匹配或文件已损坏");
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
            return Result.fail(500, "文件上传失败，请稍后重试");
        }
    }

    private boolean hasValidImageSignature(MultipartFile file, String suffix) {
        try (var inputStream = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = inputStream.read(header);
            if (bytesRead < 3) {
                return false;
            }

            if (".jpg".equals(suffix) || ".jpeg".equals(suffix)) {
                return header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
            }
            if (".png".equals(suffix)) {
                return bytesRead >= 8
                        && header[0] == (byte) 0x89
                        && header[1] == 0x50
                        && header[2] == 0x4E
                        && header[3] == 0x47
                        && header[4] == 0x0D
                        && header[5] == 0x0A
                        && header[6] == 0x1A
                        && header[7] == 0x0A;
            }
            if (".gif".equals(suffix)) {
                return bytesRead >= 6
                        && header[0] == 'G'
                        && header[1] == 'I'
                        && header[2] == 'F'
                        && header[3] == '8'
                        && (header[4] == '7' || header[4] == '9')
                        && header[5] == 'a';
            }
            if (".webp".equals(suffix)) {
                return bytesRead >= 12
                        && header[0] == 'R'
                        && header[1] == 'I'
                        && header[2] == 'F'
                        && header[3] == 'F'
                        && header[8] == 'W'
                        && header[9] == 'E'
                        && header[10] == 'B'
                        && header[11] == 'P';
            }
            return false;
        } catch (IOException e) {
            log.warn("读取上传文件头失败", e);
            return false;
        }
    }
}
