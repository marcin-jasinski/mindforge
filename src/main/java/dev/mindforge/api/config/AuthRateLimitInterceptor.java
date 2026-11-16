package dev.mindforge.api.config;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Caps how many sign-in and sign-up POSTs one client address may make per window (ADR 0038). One bucket per address
 * covers every path the interceptor is registered on; rejected attempts count. Never reads the request body. Sets
 * {@code RateLimit-Limit/Remaining/Reset}; an exhausted allowance throws, and the exception handler answers 429.
 */
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitInterceptor.class);
    private static final long MAX_TRACKED_ADDRESSES = 100_000;

    private record Window(long startMillis, int count) {}

    private final int limit;
    private final long windowMillis;
    private final Cache<String, Window> windows;

    public AuthRateLimitInterceptor(int limit, Duration window) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.windows = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(MAX_TRACKED_ADDRESSES).build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String key = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Window window = windows.asMap().compute(key, (address, old) ->
            old == null || now - old.startMillis() >= windowMillis
                ? new Window(now, 1)
                : new Window(old.startMillis(), old.count() + 1));
        long resetSeconds = Math.max(1, (window.startMillis() + windowMillis - now + 999) / 1000);
        if (window.count() > limit) {
            log.info("auth rate limit hit for {}", key);
            throw new RateLimitExceededException(limit, resetSeconds);
        }
        response.setIntHeader("RateLimit-Limit", limit);
        response.setIntHeader("RateLimit-Remaining", limit - window.count());
        response.setHeader("RateLimit-Reset", Long.toString(resetSeconds));
        return true;
    }
}
