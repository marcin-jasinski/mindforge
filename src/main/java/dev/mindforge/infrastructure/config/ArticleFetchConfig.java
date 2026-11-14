package dev.mindforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mindforge.infrastructure.security.EgressPolicy;
import dev.mindforge.infrastructure.web.HttpArticleSource;

/** Wires the {@code ArticleSource}: the strict {@link EgressPolicy} and the article fetcher's private HTTP client. */
@Configuration
public class ArticleFetchConfig {

    /** Spring closes the source, and with it the HTTP client, on shutdown. */
    @Bean
    HttpArticleSource articleSource(AppProperties properties) {
        AppProperties.Articles articles = properties.getArticles();
        return HttpArticleSource.create(new EgressPolicy(), articles.getConnectTimeout(), articles.getRequestTimeout(),
            articles.getMaxBodySize().toBytes());
    }
}
