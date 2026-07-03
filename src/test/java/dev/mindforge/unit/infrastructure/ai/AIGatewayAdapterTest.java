package dev.mindforge.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.infrastructure.ai.AIGatewayAdapter;
import dev.mindforge.infrastructure.config.AppProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

class AIGatewayAdapterTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

    @Test
    void shouldRouteSmallTierToItsConfiguredModelString() {
        AIGatewayAdapter adapter = makeAdapter();
        when(chatModel.call(any(Prompt.class))).thenReturn(fixtureResponse("small-model", 10, 20));

        adapter.complete(ModelTier.SMALL, "hello", DeadlineProfile.INTERACTIVE);

        assertThat(capturedRequestModel()).isEqualTo("small-model");
    }

    @Test
    void shouldRouteLargeTierToItsConfiguredModelString() {
        AIGatewayAdapter adapter = makeAdapter();
        when(chatModel.call(any(Prompt.class))).thenReturn(fixtureResponse("large-model", 10, 20));

        adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE);

        assertThat(capturedRequestModel()).isEqualTo("large-model");
    }

    @Test
    void shouldRouteVisionTierToItsConfiguredModelString() {
        AIGatewayAdapter adapter = makeAdapter();
        when(chatModel.call(any(Prompt.class))).thenReturn(fixtureResponse("vision-model", 10, 20));

        adapter.complete(ModelTier.VISION, "hello", DeadlineProfile.INTERACTIVE);

        assertThat(capturedRequestModel()).isEqualTo("vision-model");
    }

    @Test
    void shouldMapChatResponseIntoCompletionResult() {
        AIGatewayAdapter adapter = makeAdapter();
        when(chatModel.call(any(Prompt.class))).thenReturn(fixtureResponse("large-model", 12, 34));

        CompletionResult result = adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE);

        assertThat(result.content()).isEqualTo("hello back");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(34);
        assertThat(result.model()).isEqualTo("large-model");
        assertThat(result.provider()).isEqualTo("openrouter");
    }

    @Test
    void shouldThrowDeadlineExceededWhenCallOutlivesItsProfileTimeout() {
        AppProperties properties = makeProperties();
        properties.getAi().getDeadlines().setInteractive(Duration.ofMillis(50));
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, properties, retry(), breaker());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            return fixtureResponse("large-model", 1, 1);
        });

        assertThatThrownBy(() -> adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE))
            .isInstanceOf(DeadlineExceededException.class);
    }

    @Test
    void shouldRetryTransientFailureThenSucceed() {
        AIGatewayAdapter adapter = makeAdapter();
        when(chatModel.call(any(Prompt.class)))
            .thenThrow(new TransientAiException("503"))
            .thenReturn(fixtureResponse("large-model", 1, 1));

        CompletionResult result = adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE);

        assertThat(result.content()).isEqualTo("hello back");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void shouldNotRetryNonTransientFailure() {
        AIGatewayAdapter adapter = makeAdapter();
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("400 bad request"));

        assertThatThrownBy(() -> adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE))
            .isInstanceOf(NonTransientAiException.class);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void shouldThrowAIGatewayUnavailableWhenCircuitIsOpen() {
        CircuitBreaker openBreaker = breaker();
        openBreaker.transitionToOpenState();
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, makeProperties(), retry(), openBreaker);

        assertThatThrownBy(() -> adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE))
            .isInstanceOf(AIGatewayUnavailableException.class);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldRecordDeadlineTimeoutAsCircuitBreakerFailure() {
        AppProperties properties = makeProperties();
        properties.getAi().getDeadlines().setInteractive(Duration.ofMillis(50));
        CircuitBreaker cb = breaker();
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, properties, retry(), cb);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            return fixtureResponse("large-model", 1, 1);
        });

        assertThatThrownBy(() -> adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE))
            .isInstanceOf(DeadlineExceededException.class);

        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void shouldRetryTransientEmbeddingFailureThenSucceed() {
        AIGatewayAdapter adapter = makeAdapter();
        float[] vector = new float[] {0.1f, 0.2f};
        when(embeddingModel.embed("some text"))
            .thenThrow(new TransientAiException("503"))
            .thenReturn(vector);

        assertThat(adapter.embed("some text")).isEqualTo(vector);
        verify(embeddingModel, times(2)).embed("some text");
    }

    @Test
    void shouldDelegateEmbeddingsToEmbeddingModel() {
        AIGatewayAdapter adapter = makeAdapter();
        float[] vector = new float[] {0.1f, 0.2f};
        when(embeddingModel.embed("some text")).thenReturn(vector);

        assertThat(adapter.embed("some text")).isEqualTo(vector);
    }

    private String capturedRequestModel() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return ((OpenAiChatOptions) captor.getValue().getOptions()).getModel();
    }

    private AIGatewayAdapter makeAdapter() {
        return new AIGatewayAdapter(chatModel, embeddingModel, makeProperties(), retry(), breaker());
    }

    private static AppProperties makeProperties() {
        AppProperties properties = new AppProperties();
        properties.getAi().getModel().setSmall("small-model");
        properties.getAi().getModel().setLarge("large-model");
        properties.getAi().getModel().setVision("vision-model");
        return properties;
    }

    private static Retry retry() {
        return Retry.of("test", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(1))
            .retryExceptions(TransientAiException.class, IOException.class)
            .build());
    }

    private static CircuitBreaker breaker() {
        return CircuitBreaker.of("test", CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(10)
            .recordExceptions(TransientAiException.class, IOException.class, DeadlineExceededException.class)
            .build());
    }

    private static ChatResponse fixtureResponse(String model, int promptTokens, int completionTokens) {
        return ChatResponse.builder()
            .generations(List.of(new Generation(new AssistantMessage("hello back"))))
            .metadata(ChatResponseMetadata.builder()
                .model(model)
                .usage(new DefaultUsage(promptTokens, completionTokens))
                .build())
            .build();
    }
}
