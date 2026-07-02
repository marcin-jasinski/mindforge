package dev.mindforge.infrastructure.config;

import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.AIGatewayAdapter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the {@link AIGateway} adapter bean against the Spring AI OpenAI client. */
@Configuration
public class AiConfig {

    @Bean
    AIGateway aiGateway(ChatModel chatModel, EmbeddingModel embeddingModel, AppProperties properties) {
        return new AIGatewayAdapter(chatModel, embeddingModel, properties);
    }
}
