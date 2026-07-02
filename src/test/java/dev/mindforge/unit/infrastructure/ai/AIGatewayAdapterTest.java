package dev.mindforge.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.infrastructure.ai.AIGatewayAdapter;
import dev.mindforge.infrastructure.config.AppProperties;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;

class AIGatewayAdapterTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

    @Test
    void shouldRouteSmallTierToItsConfiguredModelString() {
        AppProperties properties = makeProperties();
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, properties);
        when(chatModel.call(any(Prompt.class))).thenReturn(fixtureResponse("small-model", 10, 20));

        adapter.complete(ModelTier.SMALL, "hello", DeadlineProfile.INTERACTIVE);

        assertThat(capturedRequestModel()).isEqualTo("small-model");
    }

    @Test
    void shouldRouteLargeTierToItsConfiguredModelString() {
        AppProperties properties = makeProperties();
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, properties);
        when(chatModel.call(any(Prompt.class))).thenReturn(fixtureResponse("large-model", 10, 20));

        adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE);

        assertThat(capturedRequestModel()).isEqualTo("large-model");
    }

    @Test
    void shouldRouteVisionTierToItsConfiguredModelString() {
        AppProperties properties = makeProperties();
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, properties);
        when(chatModel.call(any(Prompt.class))).thenReturn(fixtureResponse("vision-model", 10, 20));

        adapter.complete(ModelTier.VISION, "hello", DeadlineProfile.INTERACTIVE);

        assertThat(capturedRequestModel()).isEqualTo("vision-model");
    }

    @Test
    void shouldMapChatResponseIntoCompletionResult() {
        AppProperties properties = makeProperties();
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, properties);
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
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, properties);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            return fixtureResponse("large-model", 1, 1);
        });

        assertThatThrownBy(() -> adapter.complete(ModelTier.LARGE, "hello", DeadlineProfile.INTERACTIVE))
            .isInstanceOf(DeadlineExceededException.class);
    }

    @Test
    void shouldDelegateEmbeddingsToEmbeddingModel() {
        AppProperties properties = makeProperties();
        AIGatewayAdapter adapter = new AIGatewayAdapter(chatModel, embeddingModel, properties);
        float[] vector = new float[] {0.1f, 0.2f};
        when(embeddingModel.embed("some text")).thenReturn(vector);

        assertThat(adapter.embed("some text")).isEqualTo(vector);
    }

    private String capturedRequestModel() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return ((OpenAiChatOptions) captor.getValue().getOptions()).getModel();
    }

    private static AppProperties makeProperties() {
        AppProperties properties = new AppProperties();
        properties.getAi().getModel().setSmall("small-model");
        properties.getAi().getModel().setLarge("large-model");
        properties.getAi().getModel().setVision("vision-model");
        return properties;
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
