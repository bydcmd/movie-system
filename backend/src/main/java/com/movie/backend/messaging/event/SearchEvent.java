package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchEvent implements KeyedEvent {
    private String userId;
    private String searchKeyword;
    private Map<String, Object> filterConditions;
    private long resultCount;
    private long searchTime;

    @Override
    public Object getKeyId() {
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            return searchKeyword;
        }
        return searchTime;
    }
}
