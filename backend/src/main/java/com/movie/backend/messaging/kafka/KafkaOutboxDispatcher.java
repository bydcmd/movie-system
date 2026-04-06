package com.movie.backend.messaging.kafka;

import com.movie.backend.entity.OutboxEvent;
import com.movie.backend.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutboxDispatcher {

    private final OutboxEventMapper outboxEventMapper;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final OutboxProperties outboxProperties;

    @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-interval-ms:3600}")
    public void dispatch() {
        List<OutboxEvent> pending = outboxEventMapper.selectPending(new Date(), outboxProperties.getBatchSize());
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            try {
                kafkaEventPublisher.dispatchOutbox(event);
            } catch (Exception e) {
                log.error("Outbox dispatch failed. id={}", event.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.cleanup-interval-ms:36000000}")
    public void cleanupSent() {
        Date cutoff = new Date(System.currentTimeMillis() - outboxProperties.getCleanupRetentionMs());
        int deleted = outboxEventMapper.deleteSentBefore(cutoff, outboxProperties.getCleanupBatchSize());
        if (deleted > 0) {
            log.info("Outbox cleanup deleted {} rows before {}", deleted, cutoff);
        }
    }
}
