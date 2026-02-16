package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteEvent {
    private String userId;
    private Long movieId;
    private Long folderId;
    /**
     * ADD, REMOVE
     */
    private String operation;
}
