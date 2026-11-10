package dev.mindforge.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import io.micrometer.tracing.exporter.SpanExportingPredicate;
import io.micrometer.tracing.exporter.SpanFilter;
import io.micrometer.tracing.otel.bridge.OtelFinishedSpan;
import io.opentelemetry.api.common.AttributeKey;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Langfuse sees model work only, as metadata: no prompt, completion or error text leaves the process.
 *
 * <p>Naming convention for MindForge observations (Phase 14.3): a run or service observation is exported when its span
 * name starts with {@code mindforge.} or it carries a low-cardinality key that does, e.g. observation
 * {@code mindforge.run} with key {@code mindforge.run.id}, or contextual name {@code PageWriter} with key
 * {@code mindforge.service=PageWriter}. Spring AI's model-call spans are recognised by their {@code gen_ai.*} keys.
 */
@Configuration
public class TracingConfig {

    static final String PREFIX = "mindforge.";
    private static final String DEFAULT_HOST = "https://cloud.langfuse.com";
    private static final String PUBLIC_KEY = "mindforge.langfuse.public-key";
    private static final String SECRET_KEY = "mindforge.langfuse.secret-key";
    private static final String EXCEPTION_EVENT = "exception";
    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");

    /** Enables Spring Boot's OTLP/HTTP span exporter; without both Langfuse keys there is no exporter at all. */
    @Bean
    @Conditional(LangfuseKeysSet.class)
    public OtlpTracingConnectionDetails langfuseEndpoint(Environment env) {
        String host = StringUtils.trimTrailingCharacter(env.getProperty("mindforge.langfuse.host", DEFAULT_HOST), '/');
        return transport -> host + "/api/public/otel/v1/traces";
    }

    @Bean
    @Conditional(LangfuseKeysSet.class)
    public OtlpHttpSpanExporterBuilderCustomizer langfuseAuth(Environment env) {
        String credentials = env.getProperty(PUBLIC_KEY) + ":" + env.getProperty(SECRET_KEY);
        String basic = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return builder -> builder.addHeader("Authorization", basic);
    }

    static class LangfuseKeysSet implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            return StringUtils.hasText(env.getProperty(PUBLIC_KEY)) && StringUtils.hasText(env.getProperty(SECRET_KEY));
        }
    }

    /** Exports {@code gen_ai.*} and {@code mindforge.*} spans; HTTP, security and JDBC spans stay local. */
    @Bean
    public SpanExportingPredicate langfuseSpans() {
        return span -> span.getName().startsWith(PREFIX)
                || span.getTags().keySet().stream().anyMatch(k -> k.startsWith("gen_ai.") || k.startsWith(PREFIX));
    }

    /**
     * Keeps the exception's class name as {@code error.type}; drops the exception event (message and stack trace).
     * Known gap: Micrometer's {@code OtelSpan.error} also writes the message into the span status description, and
     * {@code FinishedSpan} offers no way to change the status.
     */
    @Bean
    public SpanFilter exceptionWithoutMessage() {
        return span -> {
            var exception = OtelFinishedSpan.toOtel(span).getEvents().stream()
                    .filter(e -> EXCEPTION_EVENT.equals(e.getName())).findFirst();
            if (exception.isEmpty()) {
                return span;
            }
            Map<String, Object> tags = new HashMap<>(span.getTypedTags());
            tags.putIfAbsent("error.type", exception.get().getAttributes().get(EXCEPTION_TYPE));
            span.setTypedTags(tags);
            span.setEvents(span.getEvents().stream().filter(e -> !EXCEPTION_EVENT.equals(e.getValue())).toList());
            return span;
        };
    }
}
