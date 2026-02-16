package com.movie.backend.messaging.event;

public enum EventType {
    VIEW_HISTORY("view_history"),
    RATING("rating"),
    COMMENT("comment"),
    COMMENT_LIKE("comment_like"),
    FAVORITE("favorite");

    private final String code;

    EventType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
