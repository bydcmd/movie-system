package com.movie.backend.messaging.event;

/**
 * Marker interface for events that include session tracking context.
 * Events implementing this interface will have session metadata attached
 * for user journey and path analysis.
 */
public interface SessionTrackedEvent extends KeyedEvent {

    /**
     * Get the session context for this event.
     * @return SessionContext containing navigation and device info
     */
    SessionContext getSessionContext();

    /**
     * Set the session context for this event.
     * @param sessionContext the session context to attach
     */
    void setSessionContext(SessionContext sessionContext);

    /**
     * Get the user ID associated with this event.
     * @return user identifier
     */
    String getUserId();
}
