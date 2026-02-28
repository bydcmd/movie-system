package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchedEvent implements KeyedEvent {
    private String userId;
    private Long movieId;
    private long watchedTime;

    @Override
    public Object getKeyId() {
        return movieId;
    }
}
