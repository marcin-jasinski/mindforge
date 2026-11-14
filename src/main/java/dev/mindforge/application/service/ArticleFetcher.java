package dev.mindforge.application.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.domain.model.ArticleFetchException;
import dev.mindforge.domain.model.ArticleFetchException.Reason;
import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.FetchedArticle;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.ArticleSource;
import dev.mindforge.domain.port.DocumentRepository;

/**
 * Fetches the articles an uploaded document links to and queues each as an {@code ARTICLE} document. Runs with no
 * transaction open: every fetch is network I/O. A URL that cannot be fetched or ingested is skipped and logged with
 * a fixed reason; the next URL goes on and nothing is stored about the skip.
 */
public class ArticleFetcher {

    private static final Logger log = LoggerFactory.getLogger(ArticleFetcher.class);

    /** An http(s) URL in Markdown link or bare form; brackets, quotes and angle brackets end it. */
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s<>\\[\\]\"'`]+");
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]\"'";
    private static final Set<BlockType> SCANNED_BLOCKS = EnumSet.of(BlockType.TEXT, BlockType.HEADING);
    private static final Set<UploadSource> SCANNED_SOURCES =
        EnumSet.of(UploadSource.API, UploadSource.CLI, UploadSource.FILE_WATCHER);

    private final DocumentRepository documents;
    private final ArticleSource articles;
    private final IngestionService ingestion;
    private final ProcessingSettings settings;
    private final int maxPerDocument;

    public ArticleFetcher(DocumentRepository documents, ArticleSource articles, IngestionService ingestion,
                          ProcessingSettings settings, int maxPerDocument) {
        this.documents = documents;
        this.articles = articles;
        this.ingestion = ingestion;
        this.settings = settings;
        this.maxPerDocument = maxPerDocument;
    }

    /** Fetches and queues the first article URLs of an uploaded document; does nothing when the switch is off. */
    public void fetchFor(UUID kbId, UUID documentId, UUID uploadedBy) {
        if (!settings.fetchExternalArticles()) {
            return;
        }
        Optional<Document> document = documents.findById(kbId, documentId);
        if (document.isEmpty()) {
            log.info("Article fetch skipped, document not found: kbId={} documentId={}", kbId, documentId);
            return;
        }
        if (!SCANNED_SOURCES.contains(document.get().uploadSource())) {
            return;
        }
        List<URI> urls = extractUrls(document.get().contentBlocks());
        int skipped = urls.size() - maxPerDocument;
        if (skipped > 0) {
            log.info("Article URLs over cap skipped: documentId={} skipped={}", documentId, skipped);
        }
        urls.stream().limit(maxPerDocument).forEach(url -> fetchOne(kbId, uploadedBy, url));
    }

    private void fetchOne(UUID kbId, UUID uploadedBy, URI url) {
        String safeUrl = safeUrl(url);
        try {
            FetchedArticle article = articles.fetch(url);
            IngestionService.ArticleAccepted accepted = ingestion.ingestArticle(kbId, uploadedBy, article.finalUrl(),
                article.title(), article.text(), article.blocks());
            if (accepted.duplicate()) {
                logSkip(kbId, Reason.DUPLICATE, safeUrl);
            }
        } catch (ArticleFetchException e) {
            logSkip(kbId, e.reason(), safeUrl);
        } catch (LessonAlreadyExistsException e) {
            logSkip(kbId, Reason.LESSON_EXISTS, safeUrl);
        } catch (LessonIdentityException e) {
            logSkip(kbId, Reason.NO_TITLE, safeUrl);
        } catch (RuntimeException e) {
            log.warn("article skipped reason=UNEXPECTED url={} exception={}", safeUrl, e.getClass().getName());
        }
    }

    private static void logSkip(UUID kbId, Reason reason, String safeUrl) {
        if (reason == Reason.LESSON_EXISTS || reason == Reason.DUPLICATE) {
            log.info("article skipped reason={} kbId={} url={}", reason, kbId, safeUrl);
        } else {
            log.warn("article skipped reason={} url={}", reason, safeUrl);
        }
    }

    /**
     * The distinct http(s) URLs in the document's TEXT and HEADING blocks, in document order, each without its
     * fragment. A match that does not parse as a URI with a host is ignored.
     */
    static List<URI> extractUrls(List<ContentBlock> blocks) {
        Set<URI> urls = new LinkedHashSet<>();
        for (ContentBlock block : blocks) {
            if (!SCANNED_BLOCKS.contains(block.blockType()) || block.content() == null) {
                continue;
            }
            Matcher matcher = URL.matcher(block.content());
            while (matcher.find()) {
                parse(trimTrailingPunctuation(withoutFragment(matcher.group()))).ifPresent(urls::add);
            }
        }
        return List.copyOf(urls);
    }

    /** The URL with userinfo, query and fragment removed, safe to log. */
    static String safeUrl(URI url) {
        String port = url.getPort() == -1 ? "" : ":" + url.getPort();
        String path = url.getRawPath() == null ? "" : url.getRawPath();
        return url.getScheme() + "://" + url.getHost() + port + path;
    }

    private static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    private static String trimTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }

    private static Optional<URI> parse(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? Optional.empty() : Optional.of(uri);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
