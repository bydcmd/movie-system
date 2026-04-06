package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class SearchEvent implements SessionTrackedEvent {
    private String userId;
    private String searchKeyword;
    private Map<String, Object> filterConditions;
    private long resultCount;
    private long searchTime;

    /**
     * Session context for user behavior tracking.
     */
    private SessionContext sessionContext;

    @Override
    public Object getKeyId() {
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            return searchKeyword;
        }
        return searchTime;
    }
}
