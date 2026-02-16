package com.movie.backend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.movie.backend.utils.ImagePathUtils;

import java.io.IOException;

/**
 * 自动为图片路径拼接域名的序列化器
 */
public class ImagePathSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(ImagePathUtils.processPath(value));
    }
}
