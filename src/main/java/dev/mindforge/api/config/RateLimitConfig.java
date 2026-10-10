package dev.mindforge.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.mindforge.infrastructure.config.AppProperties;

/**
 * Rate limits the endpoints that spend model calls: Query turns and edits, Quiz start and grading, upload and retry,
 * Lint.
 */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private static final String KB = "/api/v1/knowledge-bases/*";

    private final AppProperties.RateLimits limits;

    public RateLimitConfig(AppProperties properties) {
        this.limits = properties.getRateLimits();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(limits.getRequests(), limits.getWindow()))
            .addPathPatterns(KB + "/query-sessions/*/messages", KB + "/query-sessions/*/edits",
                KB + "/quiz-sessions", KB + "/quiz-sessions/*/answers", KB + "/documents", KB + "/documents/*/runs",
                KB + "/lint-runs");
    }
}
