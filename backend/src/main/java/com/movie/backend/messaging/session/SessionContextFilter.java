package com.movie.backend.messaging.session;

import com.movie.backend.messaging.event.SessionContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * NOTE: This filter uses a custom UUID-based sessionId for analytics tracking,
 * NOT HttpSession.getId(). The custom ID is for business-level session grouping
 * in user behavior analytics, while HttpSession.getId() is managed by the servlet
 * container for session lifecycle management.
 */

/**
 * Servlet filter that extracts session tracking information from HTTP requests.
 * Populates SessionContextHolder for downstream event enrichment.
 */
@Slf4j
@Component
public class SessionContextFilter implements Filter {

    private static final String SESSION_ID_ATTR = "tracking_session_id";
    private static final String SESSION_START_ATTR = "tracking_session_start";
    private static final String SESSION_ENTRY_ATTR = "tracking_entry_url";
    private static final String SESSION_SEQ_ATTR = "tracking_sequence";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest) {
            try {
                SessionContext context = extractSessionContext(httpRequest);
                SessionContextHolder.set(context);
                chain.doFilter(request, response);
            } finally {
                SessionContextHolder.clear();
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * Extract or create session context from HTTP request.
     *
     * @param request the HTTP request
     * @return SessionContext with tracking metadata
     */
    private SessionContext extractSessionContext(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String sessionId;
        Long sessionStartTime;
        String entryUrl;
        Integer sequenceNumber;

        if (session == null) {
            // Create new session for tracking
            session = request.getSession(true);
            sessionId = UUID.randomUUID().toString();
            sessionStartTime = System.currentTimeMillis();
            entryUrl = buildFullUrl(request);
            sequenceNumber = 1;

            session.setAttribute(SESSION_ID_ATTR, sessionId);
            session.setAttribute(SESSION_START_ATTR, sessionStartTime);
            session.setAttribute(SESSION_ENTRY_ATTR, entryUrl);
            session.setAttribute(SESSION_SEQ_ATTR, sequenceNumber);
        } else {
            // Existing session - retrieve or initialize tracking data
            sessionId = (String) session.getAttribute(SESSION_ID_ATTR);
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString();
                sessionStartTime = System.currentTimeMillis();
                entryUrl = buildFullUrl(request);
                sequenceNumber = 1;

                session.setAttribute(SESSION_ID_ATTR, sessionId);
                session.setAttribute(SESSION_START_ATTR, sessionStartTime);
                session.setAttribute(SESSION_ENTRY_ATTR, entryUrl);
            } else {
                sessionStartTime = (Long) session.getAttribute(SESSION_START_ATTR);
                entryUrl = (String) session.getAttribute(SESSION_ENTRY_ATTR);
                Integer currentSeq = (Integer) session.getAttribute(SESSION_SEQ_ATTR);
                sequenceNumber = (currentSeq != null) ? currentSeq + 1 : 1;
            }
            session.setAttribute(SESSION_SEQ_ATTR, sequenceNumber);
        }

        if (log.isDebugEnabled()) {
            log.debug("SessionContext created - HttpSession ID: {}, Tracking Session ID: {}, Sequence: {}",
                    session.getId(), sessionId, sequenceNumber);
        }

        return SessionContext.builder()
                .sessionId(sessionId)
                .pageUrl(buildFullUrl(request))
                .referrer(getReferrer(request))
                .entryUrl(entryUrl)
                .sessionStartTime(sessionStartTime)
                .sequenceNumber(sequenceNumber)
                .deviceType(detectDeviceType(request.getHeader("User-Agent")))
                .userAgent(truncate(request.getHeader("User-Agent"), 200))
                .clientTimestamp(parseClientTimestamp(request.getHeader("X-Client-Timestamp")))
                .build();
    }

    /**
     * Build full URL from request including query string.
     */
    private String buildFullUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getRequestURI());
        if (request.getQueryString() != null) {
            url.append("?").append(request.getQueryString());
        }
        return url.toString();
    }

    /**
     * Get referrer from header.
     */
    private String getReferrer(HttpServletRequest request) {
        String referrer = request.getHeader("Referer");
        return referrer != null ? truncate(referrer, 500) : null;
    }

    /**
     * Detect device type from user agent.
     */
    private String detectDeviceType(String userAgent) {
        if (userAgent == null) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            return "tablet";
        }
        return "desktop";
    }

    /**
     * Parse client timestamp header.
     */
    private Long parseClientTimestamp(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        try {
            return Long.parseLong(headerValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
