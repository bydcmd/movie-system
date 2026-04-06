package com.movie.backend.messaging.session;

import com.movie.backend.messaging.event.SessionContext;

/**
 * Thread-local holder for session context.
 * Stores session metadata during request processing for event enrichment.
 */
public class SessionContextHolder {

    private static final ThreadLocal<SessionContext> CONTEXT = new ThreadLocal<>();

    /**
     * Set the session context for the current thread.
     *
     * @param context the session context
     */
    public static void set(SessionContext context) {
        CONTEXT.set(context);
    }

    /**
     * Get the session context for the current thread.
     *
     * @return the session context, or null if not set
     */
    public static SessionContext get() {
        return CONTEXT.get();
    }

    /**
     * Clear the session context for the current thread.
     * Should be called at the end of request processing.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Check if a session context exists for the current thread.
     *
     * @return true if session context is present
     */
    public static boolean hasContext() {
        return CONTEXT.get() != null;
    }
}
