package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class WatchedEvent implements SessionTrackedEvent {
    private String userId;
    private Long movieId;
    private long watchedTime;

    /**
     * Session context for user behavior tracking.
     */
    private SessionContext sessionContext;

    @Override
    public Object getKeyId() {
        return movieId;
    }
}
