package com.movie.backend.messaging.event;

public enum EventType {
    VIEW_HISTORY("view_history"),
    RATING("rating"),
    COMMENT("comment"),
    COMMENT_LIKE("comment_like"),
    FAVORITE("favorite"),
    WATCHED("watched"),
    FAVORITE_FOLDER_ACTION("favorite_folder_action"),
    SEARCH("search"),
    USER_REGISTER("user_register"),
    USER_LOGIN("user_login");

    private final String code;

    EventType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
