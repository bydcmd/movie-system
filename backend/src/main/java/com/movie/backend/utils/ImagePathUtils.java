package com.movie.backend.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ImagePathUtils {

    @Value("${file.domain:http://localhost:8080}")
    private String domain;

    private static String staticDomain;

    @PostConstruct
    public void init() {
        staticDomain = this.domain;
    }

    /**
     * 处理图片路径
     * 1. 如果是空，返回 null
     * 2. 如果是 http 开头，直接返回
     * 3. 否则拼接配置的域名
     */
    public static String processPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (path.startsWith("http")) {
            return path;
        }
        // 确保路径以 / 开头
        String formattedPath = path.startsWith("/") ? path : "/" + path;
        return staticDomain + formattedPath;
    }
}

