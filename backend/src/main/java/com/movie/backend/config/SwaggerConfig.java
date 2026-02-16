package com.movie.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI movieOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Movie Review System API")
                        .description("Backend API for Movie Review Website")
                        .version("v1.0"))
                // 1. 配置认证方式
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                // 2. 定义具体的认证头
                .components(new Components()
                        .addSecuritySchemes("BearerAuth",
                                new SecurityScheme()
                                        .name("BearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
