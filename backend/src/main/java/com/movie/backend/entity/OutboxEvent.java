package com.movie.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "Kafka outbox event")
public class OutboxEvent {
    @Schema(description = "Primary key")
    private Long id;

    @Schema(description = "Kafka topic")
    private String topic;

    @Schema(description = "Kafka message key")
    private String messageKey;

    @Schema(description = "Event payload JSON")
    private String payload;

    @Schema(description = "Status: 0=pending, 1=sent, 2=processing, 3=failed")
    private Integer status;

    @Schema(description = "Retry count")
    private Integer retryCount;

    @Schema(description = "Next retry time")
    private Date nextRetryTime;

    @Schema(description = "Created time")
    private Date createdAt;

    @Schema(description = "Updated time")
    private Date updatedAt;

    @Schema(description = "Last error")
    private String lastError;
}
