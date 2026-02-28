package com.movie.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Configuration
public class OpenApiResultSchemaCustomizer {

    private static final String COMPONENTS_SCHEMA_PREFIX = "#/components/schemas/";
    private static final String RESULT_SCHEMA_PREFIX = "#/components/schemas/Result";
    private static final String WILDCARD_MEDIA_TYPE = "*/*";
    private static final String GENERIC_RESULT_SCHEMA_NAME = "Result";

    @Bean
    public OpenApiCustomizer resultSchemaUnwrapCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            ensureGenericResultSchema(openApi);

            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        ensureDefaultErrorResponses(operation);
                        if (operation.getResponses() == null) {
                            return;
                        }

                        operation.getResponses().values().forEach(apiResponse -> {
                            Content content = apiResponse.getContent();
                            if (content == null || content.isEmpty()) {
                                return;
                            }
                            normalizeWildcardMediaType(content);
                            content.values().forEach(mediaType -> {
                                if (mediaType == null) {
                                    return;
                                }
                                mediaType.setSchema(normalizeResultSchemaRef(mediaType.getSchema()));
                            });
                        });
                    }));

            pruneUnusedResultSchemas(openApi);
        };
    }

    private Schema<?> normalizeResultSchemaRef(Schema<?> currentSchema) {
        if (currentSchema == null) {
            return null;
        }
        String ref = currentSchema.get$ref();
        if (ref != null && ref.startsWith(RESULT_SCHEMA_PREFIX)) {
            return new Schema<>().$ref(COMPONENTS_SCHEMA_PREFIX + GENERIC_RESULT_SCHEMA_NAME);
        }
        return currentSchema;
    }

    private void normalizeWildcardMediaType(Content content) {
        if (content.containsKey(MediaType.APPLICATION_JSON_VALUE)) {
            content.remove(WILDCARD_MEDIA_TYPE);
            return;
        }
        io.swagger.v3.oas.models.media.MediaType wildcard = content.remove(WILDCARD_MEDIA_TYPE);
        if (wildcard != null) {
            content.addMediaType(MediaType.APPLICATION_JSON_VALUE, wildcard);
        }
    }

    private void ensureDefaultErrorResponses(Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }
        addResponseIfAbsent(operation, "400", "Bad Request");
        addResponseIfAbsent(operation, "401", "Unauthorized");
        addResponseIfAbsent(operation, "403", "Forbidden");
        addResponseIfAbsent(operation, "404", "Not Found");
        addResponseIfAbsent(operation, "500", "Internal Server Error");
    }

    private void addResponseIfAbsent(Operation operation, String statusCode, String description) {
        if (operation.getResponses().containsKey(statusCode)) {
            return;
        }
        ApiResponse response = new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        MediaType.APPLICATION_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType().schema(
                                new Schema<>().$ref(COMPONENTS_SCHEMA_PREFIX + GENERIC_RESULT_SCHEMA_NAME))));
        operation.getResponses().addApiResponse(statusCode, response);
    }

    private void ensureGenericResultSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new io.swagger.v3.oas.models.Components());
        }
        if (openApi.getComponents().getSchemas() == null) {
            openApi.getComponents().setSchemas(new java.util.LinkedHashMap<>());
        }
        if (openApi.getComponents().getSchemas().containsKey(GENERIC_RESULT_SCHEMA_NAME)) {
            return;
        }

        Schema<?> genericResultSchema = new ObjectSchema()
                .description("Unified API response envelope")
                .addProperty("code", new IntegerSchema().description("Business/HTTP status code").example(200))
                .addProperty("message", new StringSchema().description("Response message").example("Success"))
                .addProperty("data", new Schema<>().description("Business payload").nullable(true))
                .addProperty("timestamp", new Schema<>().type("integer").format("int64").description("Server timestamp"));

        openApi.getComponents().addSchemas(GENERIC_RESULT_SCHEMA_NAME, genericResultSchema);
    }

    private void pruneUnusedResultSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }

        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        Set<String> usedSchemaNames = collectUsedSchemaNames(openApi, schemas);

        schemas.keySet().removeIf(schemaName ->
                schemaName.startsWith("Result")
                        && !GENERIC_RESULT_SCHEMA_NAME.equals(schemaName)
                        && !usedSchemaNames.contains(schemaName));
    }

    private Set<String> collectUsedSchemaNames(OpenAPI openApi, Map<String, Schema> schemas) {
        Set<String> used = new LinkedHashSet<>();
        if (openApi.getPaths() != null) {
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                if (operation.getParameters() != null) {
                    operation.getParameters().forEach(parameter -> {
                        collectSchemaRefs(parameter.getSchema(), used);
                        Content parameterContent = parameter.getContent();
                        if (parameterContent != null) {
                            parameterContent.values().forEach(mediaType ->
                                    collectSchemaRefs(mediaType == null ? null : mediaType.getSchema(), used));
                        }
                    });
                }

                if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                    operation.getRequestBody().getContent().values().forEach(mediaType ->
                            collectSchemaRefs(mediaType == null ? null : mediaType.getSchema(), used));
                }

                if (operation.getResponses() != null) {
                    operation.getResponses().values().forEach(apiResponse -> {
                        Content content = apiResponse.getContent();
                        if (content != null) {
                            content.values().forEach(mediaType ->
                                    collectSchemaRefs(mediaType == null ? null : mediaType.getSchema(), used));
                        }
                    });
                }
            }));
        }

        ArrayDeque<String> queue = new ArrayDeque<>(used);
        while (!queue.isEmpty()) {
            String schemaName = queue.poll();
            Schema<?> schema = schemas.get(schemaName);
            if (schema == null) {
                continue;
            }

            Set<String> nested = new LinkedHashSet<>();
            collectSchemaRefs(schema, nested);
            nested.forEach(name -> {
                if (used.add(name)) {
                    queue.add(name);
                }
            });
        }

        return used;
    }

    private void collectSchemaRefs(Schema<?> schema, Set<String> refs) {
        if (schema == null) {
            return;
        }

        String schemaName = toSchemaName(schema.get$ref());
        if (schemaName != null) {
            refs.add(schemaName);
        }

        if (schema.getItems() != null) {
            collectSchemaRefs(schema.getItems(), refs);
        }

        Object additionalProperties = schema.getAdditionalProperties();
        if (additionalProperties instanceof Schema<?> additionalSchema) {
            collectSchemaRefs(additionalSchema, refs);
        }

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.values().forEach(propertySchema -> collectSchemaRefs(propertySchema, refs));
        }

        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(child -> collectSchemaRefs(child, refs));
        }
        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(child -> collectSchemaRefs(child, refs));
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(child -> collectSchemaRefs(child, refs));
        }
        if (schema.getNot() != null) {
            collectSchemaRefs(schema.getNot(), refs);
        }
    }

    private String toSchemaName(String ref) {
        if (ref == null || !ref.startsWith(COMPONENTS_SCHEMA_PREFIX)) {
            return null;
        }
        return ref.substring(COMPONENTS_SCHEMA_PREFIX.length());
    }
}
