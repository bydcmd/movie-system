package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteFolderActionEvent implements SessionTrackedEvent {
    private String userId;
    private Long folderId;
    private String folderName;
    private Integer isPublic;
    /**
     * CREATE, UPDATE, SHARE, DELETE
     */
    private String operation;

    /**
     * Session context for user behavior tracking.
     */
    private SessionContext sessionContext;

    @Override
    public Object getKeyId() {
        return folderId;
    }
}
