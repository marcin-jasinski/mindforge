package dev.mindforge.infrastructure.ai;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.config.AppProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.DisposableBean;

/**
 * {@code AIGateway} adapter backed by Spring AI's OpenAI-compatible client, pointed at
 * OpenRouter via {@code spring.ai.openai.base-url}. Model tier routes to a concrete
 * model string via {@link AppProperties}; deadline profiles are enforced by racing the
 * call against a virtual thread on a timeout, since the underlying OpenAI client only
 * accepts a timeout at client-construction time, not per request.
 *
 * <p>Resilience4j {@link Retry} and {@link CircuitBreaker} guard every provider call. The
 * retry sits <em>inside</em> the deadline race, so all attempts for one logical call share a
 * single {@link DeadlineProfile} timeout budget. Spring AI's own built-in retry is disabled
 * ({@code spring.ai.retry.max-attempts=1}) so Resilience4j is the sole retry authority.
 */
public class AIGatewayAdapter implements AIGateway, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AIGatewayAdapter.class);

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final AppProperties.Ai.Model modelRouting;
    private final AppProperties.Ai.Deadlines deadlines;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService deadlineExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AIGatewayAdapter(ChatModel chatModel,
                            EmbeddingModel embeddingModel,
                            AppProperties properties,
                            Retry retry,
                            CircuitBreaker circuitBreaker) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.modelRouting = properties.getAi().getModel();
        this.deadlines = properties.getAi().getDeadlines();
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    // ---------------------------------------------------------------------------
    // AIGateway port
    // ---------------------------------------------------------------------------

    @Override
    public CompletionResult complete(ModelTier tier, String prompt, DeadlineProfile deadline) {
        String model = resolveModel(tier);
        Prompt request = new Prompt(prompt, OpenAiChatOptions.builder().model(model).build());

        long start = System.currentTimeMillis();
        ChatResponse response = callWithDeadline(request, deadline);
        long latencyMs = System.currentTimeMillis() - start;

        AssistantMessage output = response.getResult().getOutput();
        Usage usage = response.getMetadata().getUsage();
        String resolvedModel = response.getMetadata().getModel();

        return new CompletionResult(
            output.getText(),
            usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
            usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0,
            resolvedModel != null ? resolvedModel : model,
            "openrouter",
            latencyMs,
            // ponytail: real cost accounting arrives with Phase 14 (Langfuse); OpenRouter's
            // chat-completions response carries no per-call cost field to read here.
            0.0);
    }

    @Override
    public float[] embed(String text) {
        // embed() has no deadline race (a pre-existing gap); Retry + CircuitBreaker still apply.
        try {
            return resilient(() -> embeddingModel.embed(text)).get();
        } catch (CallNotPermittedException e) {
            throw new AIGatewayUnavailableException(e);
        }
    }

    @Override
    public void destroy() {
        deadlineExecutor.shutdownNow();
    }

    // ---------------------------------------------------------------------------
    // Model-tier routing
    // ---------------------------------------------------------------------------

    private String resolveModel(ModelTier tier) {
        return switch (tier) {
            case SMALL -> modelRouting.getSmall();
            case LARGE -> modelRouting.getLarge();
            case VISION -> modelRouting.getVision();
        };
    }

    // ---------------------------------------------------------------------------
    // Deadline enforcement
    // ---------------------------------------------------------------------------

    private ChatResponse callWithDeadline(Prompt request, DeadlineProfile deadline) {
        Duration timeout = timeoutFor(deadline);
        // Retry + CircuitBreaker run inside the race, so every retry attempt shares this
        // single timeout budget rather than each attempt getting a fresh deadline.
        Supplier<ChatResponse> call = resilient(() -> chatModel.call(request));
        Future<ChatResponse> future = deadlineExecutor.submit(call::get);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("AI gateway call exceeded {} deadline of {}", deadline, timeout);
            // ponytail: cancel(true) only interrupts our virtual thread — the underlying
            // OpenAI HTTP call has no cancellation hook here and keeps running in the
            // background until it finishes or its own client-level timeout fires. Upgrade
            // path: a Spring AI API that accepts a per-request HTTP timeout/cancellation.
            future.cancel(true);
            DeadlineExceededException deadlineExceeded = new DeadlineExceededException(deadline, timeout);
            // The race times out above the circuit breaker's own bookkeeping, so record the
            // timeout as a failure explicitly — a provider that keeps timing out is unhealthy.
            // ponytail: the abandoned background call may also record its own outcome when it
            // finally returns, so a timed-out call can land two entries in the breaker window.
            // Bounded and acceptable; the real fix is the cancellation hook the deadline race
            // itself lacks (see the cancel(true) note above).
            circuitBreaker.onError(timeout.toNanos(), TimeUnit.NANOSECONDS, deadlineExceeded);
            throw deadlineExceeded;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeadlineExceededException(deadline, timeout);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CallNotPermittedException) {
                throw new AIGatewayUnavailableException(cause);
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("AI gateway call failed", cause);
        }
    }

    /**
     * Wraps a provider call with the circuit breaker (innermost, so each attempt is recorded)
     * and the retry (outermost, so an open-circuit rejection is never retried).
     */
    private <T> Supplier<T> resilient(Supplier<T> call) {
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));
    }

    private Duration timeoutFor(DeadlineProfile deadline) {
        return switch (deadline) {
            case INTERACTIVE -> deadlines.getInteractive();
            case BATCH -> deadlines.getBatch();
            case BACKGROUND -> deadlines.getBackground();
        };
    }
}
