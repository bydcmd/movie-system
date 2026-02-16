package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentEvent {
    private String userId;
    private Long movieId;
    private Long commentId;
    /**
     * 1 = short, 2 = long
     */
    private Integer type;
    /**
     * CREATE, UPDATE, DELETE
     */
    private String operation;
    private Integer contentLength;
}
