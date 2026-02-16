package com.movie.backend.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.backend.messaging.event.CommentEvent;
import com.movie.backend.messaging.event.CommentLikeEvent;
import com.movie.backend.messaging.event.EventEnvelope;
import com.movie.backend.messaging.event.EventType;
import com.movie.backend.messaging.event.FavoriteEvent;
import com.movie.backend.messaging.event.RatingEvent;
import com.movie.backend.messaging.event.ViewHistoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopicsProperties topics;

    public void publishViewHistory(ViewHistoryEvent event) {
        EventEnvelope<ViewHistoryEvent> envelope = EventEnvelope.of(EventType.VIEW_HISTORY, event);
        publishNow(topics.getViewHistory(), resolveKey(event.getUserId(), event.getMovieId()), envelope);
    }

    public void publishRatingEvent(RatingEvent event) {
        EventEnvelope<RatingEvent> envelope = EventEnvelope.of(EventType.RATING, event);
        publishAfterCommit(topics.getRating(), resolveKey(event.getUserId(), event.getMovieId()), envelope);
    }

    public void publishCommentEvent(CommentEvent event) {
        EventEnvelope<CommentEvent> envelope = EventEnvelope.of(EventType.COMMENT, event);
        publishAfterCommit(topics.getComment(), resolveKey(event.getUserId(), event.getMovieId()), envelope);
    }

    public void publishCommentLikeEvent(CommentLikeEvent event) {
        EventEnvelope<CommentLikeEvent> envelope = EventEnvelope.of(EventType.COMMENT_LIKE, event);
        publishAfterCommit(topics.getCommentLike(), resolveKey(event.getUserId(), event.getCommentId()), envelope);
    }

    public void publishFavoriteEvent(FavoriteEvent event) {
        EventEnvelope<FavoriteEvent> envelope = EventEnvelope.of(EventType.FAVORITE, event);
        publishAfterCommit(topics.getFavorite(), resolveKey(event.getUserId(), event.getMovieId()), envelope);
    }

    private void publishAfterCommit(String topic, String key, Object payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishNow(topic, key, payload);
                }
            });
            return;
        }
        publishNow(topic, key, payload);
    }

    private void publishNow(String topic, String key, Object payload) {
        if (topic == null || topic.isBlank()) {
            log.warn("Kafka topic is not configured, skip publish. payload={}", payload.getClass().getSimpleName());
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize kafka event payload: {}", payload.getClass().getSimpleName(), e);
            return;
        }
        kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send kafka event. topic={}, key={}", topic, key, ex);
            }
        });
    }

    private String resolveKey(String userId, Object fallbackId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        if (fallbackId != null) {
            return String.valueOf(fallbackId);
        }
        return Objects.toString(System.currentTimeMillis());
    }
}
