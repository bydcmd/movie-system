package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewHistoryEvent implements SessionTrackedEvent {
    private String userId;
    private Long movieId;
    private Long viewTime;

    /**
     * Session context for user behavior tracking.
     */
    private SessionContext sessionContext;

    @Override
    public Object getKeyId() {
        return movieId;
    }
}
