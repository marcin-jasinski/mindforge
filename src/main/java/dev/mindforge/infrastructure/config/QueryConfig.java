package dev.mindforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mindforge.agent.AnswerWriter;
import dev.mindforge.agent.PageSelector;
import dev.mindforge.application.service.QueryService;
import dev.mindforge.application.service.SearchService;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.domain.port.InteractionStore;
import dev.mindforge.domain.port.PageSearchQuery;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.infrastructure.ai.TracingGateway;
import io.micrometer.observation.ObservationRegistry;

/** Wires Query and page search; Query's model services call the gateway directly, outside the permit pool. */
@Configuration
public class QueryConfig {

    @Bean
    PageSelector pageSelector(AIGateway aiGateway, ObservationRegistry observationRegistry,
                              PromptLoader promptLoader) {
        return new PageSelector(
            new TracingGateway(aiGateway, PageSelector.class.getSimpleName(), observationRegistry), promptLoader);
    }

    @Bean
    AnswerWriter answerWriter(AIGateway aiGateway, ObservationRegistry observationRegistry,
                              PromptLoader promptLoader) {
        return new AnswerWriter(
            new TracingGateway(aiGateway, AnswerWriter.class.getSimpleName(), observationRegistry), promptLoader);
    }

    @Bean
    QueryService queryService(WikiStore wikiStore, InteractionStore interactionStore, PageSelector pageSelector,
                              AnswerWriter answerWriter, PageSearchQuery pageSearchQuery) {
        return new QueryService(wikiStore, interactionStore, pageSelector, answerWriter, pageSearchQuery);
    }

    @Bean
    SearchService searchService(PageSearchQuery pageSearchQuery) {
        return new SearchService(pageSearchQuery);
    }
}
