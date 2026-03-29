package com.movie.backend.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.backend.entity.OutboxEvent;
import com.movie.backend.messaging.event.CommentEvent;
import com.movie.backend.messaging.event.CommentLikeEvent;
import com.movie.backend.messaging.event.EventEnvelope;
import com.movie.backend.messaging.event.EventType;
import com.movie.backend.messaging.event.FavoriteEvent;
import com.movie.backend.messaging.event.FavoriteFolderActionEvent;
import com.movie.backend.messaging.event.KeyedEvent;
import com.movie.backend.messaging.event.RatingEvent;
import com.movie.backend.messaging.event.SearchEvent;
import com.movie.backend.messaging.event.UserLoginEvent;
import com.movie.backend.messaging.event.UserRegisterEvent;
import com.movie.backend.messaging.event.ViewHistoryEvent;
import com.movie.backend.messaging.event.WatchedEvent;
import com.movie.backend.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private static final int OUTBOX_STATUS_PENDING = 0;
    private static final int OUTBOX_STATUS_SENT = 1;
    private static final int OUTBOX_STATUS_PROCESSING = 2;
    private static final int OUTBOX_STATUS_FAILED = 3;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopicsProperties topics;
    private final OutboxEventMapper outboxEventMapper;
    private final OutboxProperties outboxProperties;

    public void publishViewHistory(ViewHistoryEvent event) {
        publishEvent(topics.getViewHistory(), EventType.VIEW_HISTORY, event, false);
    }

    public void publishRatingEvent(RatingEvent event) {
        publishEvent(topics.getRating(), EventType.RATING, event, true);
    }

    public void publishCommentEvent(CommentEvent event) {
        publishEvent(topics.getComment(), EventType.COMMENT, event, true);
    }

    public void publishCommentLikeEvent(CommentLikeEvent event) {
        publishEvent(topics.getCommentLike(), EventType.COMMENT_LIKE, event, true);
    }

    public void publishFavoriteEvent(FavoriteEvent event) {
        publishEvent(topics.getFavorite(), EventType.FAVORITE, event, true);
    }

    public void publishWatchedEvent(WatchedEvent event) {
        publishEvent(topics.getWatched(), EventType.WATCHED, event, true);
    }

    public void publishFavoriteFolderActionEvent(FavoriteFolderActionEvent event) {
        publishEvent(topics.getFavoriteFolderAction(), EventType.FAVORITE_FOLDER_ACTION, event, true);
    }

    public void publishSearchEvent(SearchEvent event) {
        publishEvent(topics.getSearch(), EventType.SEARCH, event, false);
    }

    public void publishUserRegisterEvent(UserRegisterEvent event) {
        publishEvent(topics.getUserRegister(), EventType.USER_REGISTER, event, true);
    }

    public void publishUserLoginEvent(UserLoginEvent event) {
        publishEvent(topics.getUserLogin(), EventType.USER_LOGIN, event, false);
    }

    private <T extends KeyedEvent> void publishEvent(String topic, EventType eventType, T event, boolean afterCommit) {
        EventEnvelope<T> envelope = EventEnvelope.of(eventType, event);
        String key = resolveKey(event);
        if (afterCommit) {
            publishAfterCommit(topic, key, envelope);
            return;
        }
        publishNow(topic, key, envelope);
    }

    private void publishAfterCommit(String topic, String key, Object payload) {
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
        OutboxEvent outbox = new OutboxEvent();
        outbox.setTopic(topic);
        outbox.setMessageKey(key);
        outbox.setPayload(json);
        outbox.setStatus(OUTBOX_STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(new Date());
        outboxEventMapper.insert(outbox);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchOutbox(outbox);
                }
            });
            return;
        }
        dispatchOutbox(outbox);
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
        log.info("Sending Kafka message: topic={}, key={}, payload={}", topic, key, json);
        kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send kafka event. topic={}, key={}", topic, key, ex);
            } else {
                log.info("Kafka message sent successfully. topic={}, key={}, partition={}, offset={}",
                        topic, key, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    public void dispatchOutbox(OutboxEvent outbox) {
        if (outbox == null || outbox.getId() == null) {
            return;
        }
        Date lockUntil = new Date(System.currentTimeMillis() + outboxProperties.getProcessingTimeoutMs());
        int claimed = outboxEventMapper.markProcessing(outbox.getId(), lockUntil);
        if (claimed == 0) {
            return;
        }
        kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), outbox.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        outboxEventMapper.markSent(outbox.getId());
                        return;
                    }
                    handleOutboxFailure(outbox, ex);
                });
    }

    private void handleOutboxFailure(OutboxEvent outbox, Throwable ex) {
        int nextRetry = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        int status = nextRetry >= outboxProperties.getMaxRetries() ? OUTBOX_STATUS_FAILED : OUTBOX_STATUS_PENDING;
        Date nextRetryTime = nextRetryTime(nextRetry);
        String error = shortError(ex);
        outboxEventMapper.markFailed(outbox.getId(), status, nextRetry, nextRetryTime, error);
        log.error("Failed to send outbox event. id={}, topic={}, key={}, retry={}",
                outbox.getId(), outbox.getTopic(), outbox.getMessageKey(), nextRetry, ex);
    }

    private Date nextRetryTime(int retryCount) {
        long factor = 1L << Math.min(Math.max(retryCount - 1, 0), 6);
        long baseDelay = outboxProperties.getBaseDelayMs();
        long maxDelay = outboxProperties.getMaxDelayMs();
        long delay = Math.min(maxDelay, baseDelay * factor);
        return new Date(System.currentTimeMillis() + delay);
    }

    private String shortError(Throwable ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.toString();
        }
        if (msg.length() > 500) {
            return msg.substring(0, 500);
        }
        return msg;
    }

    private String resolveKey(KeyedEvent event) {
        return resolveKey(event.getUserId(), event.getKeyId());
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
