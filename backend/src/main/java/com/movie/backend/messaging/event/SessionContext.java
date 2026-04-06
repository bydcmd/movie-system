package com.movie.backend.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session context for user behavior tracking.
 * Captures navigation path, device info, and session metadata for path analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {

    /**
     * Unique session identifier (UUID) for grouping sequential actions
     */
    private String sessionId;

    /**
     * Current page URL path (e.g., /movies/123, /search)
     */
    private String pageUrl;

    /**
     * Referrer URL path (previous page)
     */
    private String referrer;

    /**
     * Entry point URL for this session (first page visited)
     */
    private String entryUrl;

    /**
     * Timestamp when session started (milliseconds)
     */
    private Long sessionStartTime;

    /**
     * Sequence number of this action within the session (1, 2, 3...)
     */
    private Integer sequenceNumber;

    /**
     * Device type: desktop, mobile, tablet
     */
    private String deviceType;

    /**
     * Browser user agent string (truncated)
     */
    private String userAgent;

    /**
     * Screen resolution (e.g., 1920x1080)
     */
    private String screenResolution;

    /**
     * Time spent on previous page in milliseconds (if available)
     */
    private Long timeOnPreviousPage;

    /**
     * Client-side timestamp when event occurred
     */
    private Long clientTimestamp;

    /**
     * A/B test variant identifier (if applicable)
     */
    private String experimentId;
}
