package dev.mindforge.infrastructure.event;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.mindforge.application.service.ArticleFetcher;
import dev.mindforge.domain.model.DomainEvent;

/**
 * Fetches a new document's articles after its upload commits, on its own thread, so the upload never waits for a
 * fetch. Best-effort: a missed event or a failed task fetches nothing and is not retried.
 */
public class ArticleFetchTriggers {

    private static final Logger log = LoggerFactory.getLogger(ArticleFetchTriggers.class);

    private final ArticleFetcher fetcher;
    private final Executor executor;

    public ArticleFetchTriggers(ArticleFetcher fetcher, Executor executor) {
        this.fetcher = fetcher;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentAccepted(DomainEvent.DocumentAccepted event) {
        executor.execute(() -> fetch(event));
    }

    private void fetch(DomainEvent.DocumentAccepted event) {
        try {
            fetcher.fetchFor(event.knowledgeBaseId(), event.documentId(), event.uploadedBy());
        } catch (RuntimeException e) {
            log.warn("Article fetch failed: kbId={} documentId={} error={}", event.knowledgeBaseId(),
                event.documentId(), e.getClass().getSimpleName());
        }
    }
}
