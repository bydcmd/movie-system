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

    public static <T> EventEnvelope<T> of(EventType type, T data) {
        EventEnvelope<T> envelope = new EventEnvelope<>();
        envelope.setEventId(UUID.randomUUID().toString());
        envelope.setEventType(type.getCode());
        envelope.setOccurredAt(System.currentTimeMillis());
        envelope.setData(data);
        return envelope;
    }
}
