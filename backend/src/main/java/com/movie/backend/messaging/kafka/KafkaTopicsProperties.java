package com.movie.backend.messaging.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicsProperties {
    private String viewHistory = "movie-view-history";
    private String rating = "movie-rating-events";
    private String comment = "movie-comment-events";
    private String commentLike = "movie-comment-like-events";
    private String favorite = "movie-favorite-events";
    private String watched = "movie-watched-events";
    private String favoriteFolderAction = "movie-favorite-folder-action-events";
    private String search = "movie-search-events";
    private String userRegister = "movie-user-register-events";
    private String userLogin = "movie-user-login-events";
}
