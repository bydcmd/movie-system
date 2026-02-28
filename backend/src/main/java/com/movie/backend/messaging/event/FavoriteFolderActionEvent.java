package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteFolderActionEvent implements KeyedEvent {
    private String userId;
    private Long folderId;
    private String folderName;
    private Integer isPublic;
    /**
     * CREATE, UPDATE, SHARE, DELETE
     */
    private String operation;

    @Override
    public Object getKeyId() {
        return folderId;
    }
}
