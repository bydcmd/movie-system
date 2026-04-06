package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
    private String eventId;
    private String eventType;
    private long occurredAt;
    private T data;

    /**
     * Session context for user behavior tracking and path analysis.
     * Optional - only populated when session tracking is enabled for the event.
     */
    private SessionContext sessionContext;

    public static <T> EventEnvelope<T> of(EventType type, T data) {
        EventEnvelope<T> envelope = new EventEnvelope<>();
        envelope.setEventId(UUID.randomUUID().toString());
        envelope.setEventType(type.getCode());
        envelope.setOccurredAt(System.currentTimeMillis());
        envelope.setData(data);
        return envelope;
    }

    /**
     * Create an envelope with session context for user behavior tracking.
     *
     * @param type the event type
     * @param data the event payload
     * @param sessionContext session tracking metadata
     * @return a new EventEnvelope with session context
     */
    public static <T extends SessionTrackedEvent> EventEnvelope<T> ofWithSession(EventType type, T data, SessionContext sessionContext) {
        EventEnvelope<T> envelope = of(type, data);
        envelope.setSessionContext(sessionContext);
        data.setSessionContext(sessionContext);
        return envelope;
    }
}
