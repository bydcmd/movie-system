package com.movie.backend.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.backend.entity.OutboxEvent;
import com.movie.backend.messaging.event.EventEnvelope;
import com.movie.backend.messaging.event.EventType;
import com.movie.backend.messaging.event.KeyedEvent;
import com.movie.backend.messaging.event.SessionContext;
import com.movie.backend.messaging.event.SessionTrackedEvent;
import com.movie.backend.messaging.session.SessionContextHolder;
import com.movie.backend.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.Objects;

/**
 * Writes events to the Outbox table.
 * Events are stored in EventEnvelope JSON format, compatible with Spark's JDBC reader.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int OUTBOX_STATUS_PENDING = 0;

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public void publishViewHistory(com.movie.backend.messaging.event.ViewHistoryEvent event) {
        publishEvent(EventType.VIEW_HISTORY, event, true);
    }

    public void publishRatingEvent(com.movie.backend.messaging.event.RatingEvent event) {
        publishEvent(EventType.RATING, event, true);
    }

    public void publishCommentEvent(com.movie.backend.messaging.event.CommentEvent event) {
        publishEvent(EventType.COMMENT, event, true);
    }

    public void publishCommentLikeEvent(com.movie.backend.messaging.event.CommentLikeEvent event) {
        publishEvent(EventType.COMMENT_LIKE, event, true);
    }

    public void publishFavoriteEvent(com.movie.backend.messaging.event.FavoriteEvent event) {
        publishEvent(EventType.FAVORITE, event, true);
    }

    public void publishWatchedEvent(com.movie.backend.messaging.event.WatchedEvent event) {
        publishEvent(EventType.WATCHED, event, true);
    }

    public void publishFavoriteFolderActionEvent(com.movie.backend.messaging.event.FavoriteFolderActionEvent event) {
        publishEvent(EventType.FAVORITE_FOLDER_ACTION, event, true);
    }

    public void publishSearchEvent(com.movie.backend.messaging.event.SearchEvent event) {
        publishEvent(EventType.SEARCH, event, false);
    }

    public void publishUserRegisterEvent(com.movie.backend.messaging.event.UserRegisterEvent event) {
        publishEvent(EventType.USER_REGISTER, event, true);
    }

    public void publishUserLoginEvent(com.movie.backend.messaging.event.UserLoginEvent event) {
        publishEvent(EventType.USER_LOGIN, event, false);
    }

    private <T extends KeyedEvent> void publishEvent(EventType eventType, T event, boolean afterCommit) {
        EventEnvelope<T> envelope = createEnvelope(eventType, event);
        String key = resolveKey(event);

        if (afterCommit) {
            writeOutboxAfterCommit(eventType, envelope, key);
            return;
        }
        writeOutboxNow(envelope, key);
    }

    private <T extends KeyedEvent> EventEnvelope<T> createEnvelope(EventType eventType, T event) {
        SessionContext sessionContext = SessionContextHolder.get();
        if (sessionContext != null && event instanceof SessionTrackedEvent sessionTrackedEvent) {
            sessionTrackedEvent.setSessionContext(sessionContext);
            EventEnvelope<T> envelope = EventEnvelope.of(eventType, event);
            envelope.setSessionContext(sessionContext);
            return envelope;
        }
        return EventEnvelope.of(eventType, event);
    }

    private void writeOutboxAfterCommit(EventType eventType, EventEnvelope<?> envelope, String key) {
        String topic = resolveTopic(eventType);
        String json = serialize(envelope);
        if (json == null) {
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
                    log.debug("Outbox event committed. topic={}, key={}", topic, key);
                }
            });
        }
    }

    private void writeOutboxNow(EventEnvelope<?> envelope, String key) {
        String topic = resolveTopic(null);
        String json = serialize(envelope);
        if (json == null) {
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
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload: {}", payload.getClass().getSimpleName(), e);
            return null;
        }
    }

    private String resolveTopic(EventType eventType) {
        if (eventType == null) {
            return "unknown";
        }
        return switch (eventType) {
            case VIEW_HISTORY -> "movie-view-history";
            case RATING -> "movie-rating-events";
            case COMMENT -> "movie-comment-events";
            case COMMENT_LIKE -> "movie-comment-like-events";
            case FAVORITE -> "movie-favorite-events";
            case WATCHED -> "movie-watched-events";
            case FAVORITE_FOLDER_ACTION -> "movie-favorite-folder-action-events";
            case SEARCH -> "movie-search-events";
            case USER_REGISTER -> "movie-user-register-events";
            case USER_LOGIN -> "movie-user-login-events";
        };
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
