package com.movie.backend.messaging.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicsProperties {
    private String viewHistory;
    private String rating;
    private String comment;
    private String commentLike;
    private String favorite;
    private String watched;
    private String favoriteFolderAction;
    private String search;
    private String userRegister;
    private String userLogin;
}
