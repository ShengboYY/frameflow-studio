package com.frameflow.studio.trigger.http.identity;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs before context/CSRF loading, so an expired session cannot authorize a write. */
public class SessionLifetimeFilter extends OncePerRequestFilter {
    public static final String STARTED_AT = SessionLifetimeFilter.class.getName() + ".startedAt";
    public static final String LAST_ACTIVITY = SessionLifetimeFilter.class.getName() + ".lastActivity";
    private final Clock clock;

    public SessionLifetimeFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Instant started = (Instant) session.getAttribute(STARTED_AT);
            Instant last = (Instant) session.getAttribute(LAST_ACTIVITY);
            Instant now = clock.instant();
            if (started != null && (expired(started, now, Duration.ofHours(8))
                    || last == null || expired(last, now, Duration.ofMinutes(30)))) {
                session.invalidate();
            } else if (started != null) {
                session.setAttribute(LAST_ACTIVITY, now);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean expired(Instant from, Instant now, Duration limit) {
        return !now.isBefore(from.plus(limit));
    }
}
