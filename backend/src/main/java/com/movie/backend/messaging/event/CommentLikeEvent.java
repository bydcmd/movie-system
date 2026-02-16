package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentLikeEvent {
    private String userId;
    private Long commentId;
    /**
     * LIKE, UNLIKE
     */
    private String operation;
}
