package dev.mindforge.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SpanExporters;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.mindforge.infrastructure.config.TracingConfig;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;

/** The Langfuse exporter exists only with both keys, wired through Spring Boot's OTLP auto-configuration. */
@Tag("integration")
class TracingConfigTest {

    @Test
    void shouldExportToLangfuseOnlyWhenBothKeysAreSet() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OtlpTracingAutoConfiguration.class))
                .withUserConfiguration(TracingConfig.class)
                .withPropertyValues("mindforge.langfuse.host=https://langfuse.example/");

        runner.withPropertyValues("mindforge.langfuse.public-key=pk-lf-1", "mindforge.langfuse.secret-key=sk-lf-1")
                .run(ctx -> {
                    assertThat(ctx.getBean(OtlpHttpSpanExporter.class).toString())
                            .contains("endpoint=https://langfuse.example/api/public/otel/v1/traces")
                            .contains("Authorization=OBFUSCATED");
                    // Boot's batch processor exports through the wrapper, never the raw exporter
                    assertThat(ctx.getBean(SpanExporters.class).list()).singleElement()
                            .isNotInstanceOf(OtlpHttpSpanExporter.class);
                });
        runner.withPropertyValues("mindforge.langfuse.public-key=pk-lf-1", "mindforge.langfuse.secret-key=")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OtlpHttpSpanExporter.class));
        runner.withPropertyValues("mindforge.langfuse.secret-key=sk-lf-1")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OtlpHttpSpanExporter.class));
    }
}
