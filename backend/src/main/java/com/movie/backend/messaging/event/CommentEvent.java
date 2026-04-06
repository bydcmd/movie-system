package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class CommentEvent implements SessionTrackedEvent {
    private String userId;
    private Long movieId;
    private Long commentId;
    /**
     * 1 = short, 2 = long
     */
    private Integer type;
    /**
     * CREATE, UPDATE, DELETE
     */
    private String operation;
    private Integer contentLength;

    /**
     * Session context for user behavior tracking.
     */
    private SessionContext sessionContext;

    @Override
    public Object getKeyId() {
        return movieId;
    }
}
