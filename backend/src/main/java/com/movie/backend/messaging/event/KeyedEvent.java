package com.movie.backend.messaging.event;

public interface KeyedEvent {
    String getUserId();

    Object getKeyId();
}
