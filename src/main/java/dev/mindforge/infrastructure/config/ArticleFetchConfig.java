package dev.mindforge.infrastructure.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import dev.mindforge.application.service.ArticleFetcher;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.port.ArticleSource;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.event.ArticleFetchTriggers;
import dev.mindforge.infrastructure.security.EgressPolicy;
import dev.mindforge.infrastructure.web.HttpArticleSource;

/**
 * Wires article fetching: the strict {@link EgressPolicy}, the article fetcher's private HTTP client, the
 * {@link ArticleFetcher} and its after-commit trigger.
 */
@Configuration
public class ArticleFetchConfig {

    /** Spring closes the source, and with it the HTTP client, on shutdown. */
    @Bean
    HttpArticleSource articleSource(AppProperties properties) {
        AppProperties.Articles articles = properties.getArticles();
        return HttpArticleSource.create(new EgressPolicy(), articles.getConnectTimeout(), articles.getRequestTimeout(),
            articles.getMaxBodySize().toBytes());
    }

    @Bean
    ArticleFetcher articleFetcher(DocumentRepository documentRepository, ArticleSource articleSource,
                                  IngestionService ingestionService, ProcessingSettings processingSettings,
                                  AppProperties properties) {
        return new ArticleFetcher(documentRepository, articleSource, ingestionService, processingSettings,
            properties.getArticles().getMaxPerDocument());
    }

    /**
     * Not in the {@code cli} profile: the CLI exits after its own run and would cut a background fetch off, so the
     * CLI never fetches articles.
     */
    @Bean
    @Profile("!cli")
    ArticleFetchTriggers articleFetchTriggers(ArticleFetcher articleFetcher, Executor runExecutor) {
        return new ArticleFetchTriggers(articleFetcher, runExecutor);
    }
}
