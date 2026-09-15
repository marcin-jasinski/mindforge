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

/** Wires Query and page search; Query's model services call the gateway directly, outside the permit pool. */
@Configuration
public class QueryConfig {

    @Bean
    PageSelector pageSelector(AIGateway aiGateway, PromptLoader promptLoader) {
        return new PageSelector(aiGateway, promptLoader);
    }

    @Bean
    AnswerWriter answerWriter(AIGateway aiGateway, PromptLoader promptLoader) {
        return new AnswerWriter(aiGateway, promptLoader);
    }

    @Bean
    QueryService queryService(WikiStore wikiStore, InteractionStore interactionStore, PageSelector pageSelector,
                              AnswerWriter answerWriter) {
        return new QueryService(wikiStore, interactionStore, pageSelector, answerWriter);
    }

    @Bean
    SearchService searchService(PageSearchQuery pageSearchQuery) {
        return new SearchService(pageSearchQuery);
    }
}
