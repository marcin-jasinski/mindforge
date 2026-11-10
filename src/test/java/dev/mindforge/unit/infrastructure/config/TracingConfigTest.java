package dev.mindforge.unit.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mindforge.infrastructure.config.TracingConfig;
import io.micrometer.tracing.otel.bridge.CompositeSpanExporter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
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
            // what Micrometer's OtelSpan.error does
            span.recordException(new IllegalStateException("provider quoted the secret prompt"));
            span.setStatus(StatusCode.ERROR, "provider quoted the secret prompt");
            span.end();
        });

        SpanData span = exported.getFirst();
        assertThat(span.getAttributes().get(AttributeKey.stringKey("error.type")))
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name"))).isEqualTo("chat");
        assertThat(span.getEvents()).extracting(EventData::getName).doesNotContain("exception");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEmpty();
        assertThat(span.toString()).doesNotContain("secret prompt");
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
        SpanExporter composite = new CompositeSpanExporter(List.of(TracingConfig.withoutStatusMessage(capture)),
                List.of(config.langfuseSpans()), List.of(), List.of(config.exceptionWithoutMessage()));
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(composite)).build()) {
            emit.accept(provider.get("test"));
        }
        return captured;
    }
}
