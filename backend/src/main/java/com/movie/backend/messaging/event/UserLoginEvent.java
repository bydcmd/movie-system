package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginEvent implements KeyedEvent {
    private String userId;

    @Override
    public Object getKeyId() {
        return userId;
    }
}
