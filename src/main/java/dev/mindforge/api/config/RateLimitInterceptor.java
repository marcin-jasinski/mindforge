package dev.mindforge.api.config;

import java.time.Duration;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import dev.mindforge.domain.model.RateLimitExceededException;

/**
 * Caps how many model-calling POSTs a user may make per window. In-process: the deployment has one live instance.
 * Sets {@code RateLimit-Limit/Remaining/Reset}; an exhausted allowance throws, and the exception handler answers 429.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private record Window(long startMillis, int count) {}

    private final int limit;
    private final long windowMillis;
    private final Cache<UUID, Window> windows;

    public RateLimitInterceptor(int limit, Duration window) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.windows = Caffeine.newBuilder().expireAfterWrite(window).build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!"POST".equals(request.getMethod()) || !(auth != null && auth.getPrincipal() instanceof CurrentUser user)) {
            return true;
        }
        long now = System.currentTimeMillis();
        Window window = windows.asMap().compute(user.userId(), (id, old) ->
            old == null || now - old.startMillis() >= windowMillis
                ? new Window(now, 1)
                : new Window(old.startMillis(), old.count() + 1));
        long resetSeconds = Math.max(1, (window.startMillis() + windowMillis - now + 999) / 1000);
        if (window.count() > limit) {
            throw new RateLimitExceededException(limit, resetSeconds);
        }
        response.setIntHeader("RateLimit-Limit", limit);
        response.setIntHeader("RateLimit-Remaining", limit - window.count());
        response.setHeader("RateLimit-Reset", Long.toString(resetSeconds));
        return true;
    }
}
