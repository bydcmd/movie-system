package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class ViewHistoryEvent implements SessionTrackedEvent {
    private String userId;
    private Long movieId;
    private long viewTime;

    /**
     * Session context for user behavior tracking.
     * Populated automatically by KafkaEventPublisher when SessionContextHolder has context.
     */
    private SessionContext sessionContext;

    @Override
    public Object getKeyId() {
        return movieId;
    }
}
