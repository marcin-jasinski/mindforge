package dev.mindforge.domain.model;

import java.net.URI;
import java.util.List;

/**
 * A web article fetched by an {@code ArticleSource}: the URL it was finally served from after redirects, its title
 * ({@code null} when the page has none), its plain text and its HEADING, TEXT and CODE blocks in order.
 */
public record FetchedArticle(URI finalUrl, String title, String text, List<ContentBlock> blocks) {

    public FetchedArticle {
        blocks = List.copyOf(blocks);
    }
}
