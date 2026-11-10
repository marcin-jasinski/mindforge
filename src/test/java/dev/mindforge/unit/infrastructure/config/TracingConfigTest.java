package dev.mindforge.unit.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.mindforge.infrastructure.config.TracingConfig;
import io.micrometer.tracing.otel.bridge.CompositeSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/** What reaches Langfuse: spans pass through the same {@link CompositeSpanExporter} Spring Boot builds. */
class TracingConfigTest {

    @Test
    void shouldExportOnlyModelCallsAndMindForgeObservations() {
        List<SpanData> exported = export(tracer -> {
            tracer.spanBuilder("chat gpt-4o").setAttribute("gen_ai.operation.name", "chat").startSpan().end();
            tracer.spanBuilder("PageWriter").setAttribute("mindforge.service", "PageWriter").startSpan().end();
            tracer.spanBuilder("mindforge.run").startSpan().end();
            tracer.spanBuilder("http post /api/kb").setAttribute("http.method", "POST").startSpan().end();
            tracer.spanBuilder("security filterchain before").startSpan().end();
        });

        assertThat(exported).extracting(SpanData::getName)
                .containsExactly("chat gpt-4o", "PageWriter", "mindforge.run");
    }

    @Test
    void shouldKeepExceptionClassNameAndDropItsMessageAndEvent() {
        List<SpanData> exported = export(tracer -> {
            Span span = tracer.spanBuilder("chat gpt-4o").setAttribute("gen_ai.operation.name", "chat").startSpan();
            span.recordException(new IllegalStateException("provider quoted the secret prompt"));
            span.end();
        });

        SpanData span = exported.getFirst();
        assertThat(span.getAttributes().get(AttributeKey.stringKey("error.type")))
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name"))).isEqualTo("chat");
        assertThat(span.getEvents()).extracting(EventData::getName).doesNotContain("exception");
        assertThat(span.toString()).doesNotContain("secret prompt");
    }

    @Test
    void shouldExportToLangfuseOnlyWhenBothKeysAreSet() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OtlpTracingAutoConfiguration.class))
                .withUserConfiguration(TracingConfig.class)
                .withPropertyValues("mindforge.langfuse.host=https://langfuse.example/");

        runner.withPropertyValues("mindforge.langfuse.public-key=pk-lf-1", "mindforge.langfuse.secret-key=sk-lf-1")
                .run(ctx -> assertThat(ctx.getBean(OtlpHttpSpanExporter.class).toString())
                        .contains("endpoint=https://langfuse.example/api/public/otel/v1/traces")
                        .contains("Authorization=OBFUSCATED"));
        runner.withPropertyValues("mindforge.langfuse.public-key=pk-lf-1", "mindforge.langfuse.secret-key=")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OtlpHttpSpanExporter.class));
        runner.withPropertyValues("mindforge.langfuse.secret-key=sk-lf-1")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OtlpHttpSpanExporter.class));
    }

    private static List<SpanData> export(java.util.function.Consumer<Tracer> emit) {
        TracingConfig config = new TracingConfig();
        List<SpanData> captured = new ArrayList<>();
        SpanExporter capture = new SpanExporter() {
            @Override public CompletableResultCode export(Collection<SpanData> spans) {
                captured.addAll(spans);
                return CompletableResultCode.ofSuccess();
            }
            @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
        SpanExporter composite = new CompositeSpanExporter(List.of(capture),
                List.of(config.langfuseSpans()), List.of(), List.of(config.exceptionWithoutMessage()));
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(composite)).build()) {
            emit.accept(provider.get("test"));
        }
        return captured;
    }
}
