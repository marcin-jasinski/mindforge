package dev.mindforge.infrastructure.ai;

import java.time.Duration;
import java.util.Optional;
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
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.config.AppProperties;
import com.openai.core.JsonValue;
import com.openai.models.completions.CompletionUsage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.context.ContextSnapshotFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

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
    private static final ContextSnapshotFactory CONTEXT = ContextSnapshotFactory.builder().build();

    private final ChatModel chatModel;
    private final AppProperties.Ai.Model modelRouting;
    private final AppProperties.Ai.Deadlines deadlines;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService deadlineExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AIGatewayAdapter(ChatModel chatModel,
                            AppProperties properties,
                            Retry retry,
                            CircuitBreaker circuitBreaker) {
        this.chatModel = chatModel;
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
        return complete(new Prompt(prompt, options(model)), model, deadline);
    }

    @Override
    public CompletionResult complete(ModelTier tier, String prompt, ImageInput image, DeadlineProfile deadline) {
        if (tier != ModelTier.VISION) {
            throw new IllegalArgumentException("An image needs the VISION tier");
        }
        String model = resolveModel(tier);
        UserMessage message = UserMessage.builder()
            .text(prompt)
            .media(new Media(MimeTypeUtils.parseMimeType(image.mimeType()), new ByteArrayResource(image.data())))
            .build();
        return complete(new Prompt(message, options(model)), model, deadline);
    }

    private static OpenAiChatOptions options(String model) {
        return OpenAiChatOptions.builder().model(model).build();
    }

    private CompletionResult complete(Prompt request, String model, DeadlineProfile deadline) {
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
            costUsd(response));
    }

    /** OpenRouter's charge in USD, or {@code null} when the response carries none (not measured, never 0). */
    static Double costUsd(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || !(usage.getNativeUsage() instanceof CompletionUsage nativeUsage)) {
            return null;
        }
        JsonValue cost = nativeUsage._additionalProperties().get("cost");
        Optional<Number> number = cost == null ? Optional.empty() : cost.asNumber();
        return number.map(Number::doubleValue).orElse(null);
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
        // the caller's observation (its model service) becomes the parent of Spring AI's chat span
        Future<ChatResponse> future = deadlineExecutor.submit(CONTEXT.captureAll().wrap(call::get));
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
            if (cause instanceof RuntimeException runtimeException
                && runtimeException.getClass().getPackageName().startsWith("dev.mindforge.domain")) {
                throw runtimeException;
            }
            // the provider's message can quote the request or the response, so a run records fixed text
            log.warn("AI provider call failed", cause);
            throw new AIGatewayUnavailableException("AI provider call failed — check the model server", cause);
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
