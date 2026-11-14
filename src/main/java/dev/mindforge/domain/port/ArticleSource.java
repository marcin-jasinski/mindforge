package dev.mindforge.domain.port;

import java.net.URI;

import dev.mindforge.domain.model.ArticleFetchException;
import dev.mindforge.domain.model.FetchedArticle;

/** Fetches one article URL from the web and converts it to plain text. Not tenant-scoped: a URL has no owner. */
public interface ArticleSource {

    /**
     * Fetches {@code url}, following redirects, and returns the converted article.
     *
     * @throws ArticleFetchException with a fixed {@link ArticleFetchException.Reason} for every per-URL failure
     */
    FetchedArticle fetch(URI url);
}
