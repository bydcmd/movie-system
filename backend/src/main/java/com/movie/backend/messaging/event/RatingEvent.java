package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingEvent {
    private String userId;
    private Long movieId;
    private Integer rating;
    /**
     * CREATE, UPDATE, DELETE, CLEAR
     */
    private String operation;
    private String ratingTime;
}
