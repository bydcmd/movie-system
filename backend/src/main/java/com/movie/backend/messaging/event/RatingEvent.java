package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class RatingEvent implements SessionTrackedEvent {
    private String userId;
    private Long movieId;
    private Integer rating;
    /**
     * CREATE, UPDATE, DELETE, CLEAR
     */
    private String operation;
    private String ratingTime;

    /**
     * Session context for user behavior tracking.
     */
    private SessionContext sessionContext;

    @Override
    public Object getKeyId() {
        return movieId;
    }
}
