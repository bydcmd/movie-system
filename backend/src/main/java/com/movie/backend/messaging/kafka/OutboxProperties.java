package com.movie.backend.messaging.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.kafka.outbox")
public class OutboxProperties {
    private int batchSize = 100;
    private int maxRetries = 10;
    private long baseDelayMs = 5_000L;
    private long maxDelayMs = 120_000L;
    private long processingTimeoutMs = 60_000L;
    private long cleanupRetentionMs = 7 * 24 * 60 * 60 * 1000L;
    private int cleanupBatchSize = 500;
    private long pollIntervalMs = 5_000L;
}
