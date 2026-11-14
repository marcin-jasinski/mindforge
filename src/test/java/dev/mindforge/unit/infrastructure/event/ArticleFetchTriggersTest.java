package dev.mindforge.unit.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mindforge.application.service.ArticleFetcher;
import dev.mindforge.domain.model.DomainEvent;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.infrastructure.event.ArticleFetchTriggers;

class ArticleFetchTriggersTest {

    private static final UUID KB = UUID.randomUUID();
    private static final UUID DOCUMENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final ArticleFetcher fetcher = mock(ArticleFetcher.class);
    private final List<Runnable> submitted = new ArrayList<>();
    private final ArticleFetchTriggers triggers = new ArticleFetchTriggers(fetcher, submitted::add);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger triggersLog = (Logger) LoggerFactory.getLogger(ArticleFetchTriggers.class);

    ArticleFetchTriggersTest() {
        logs.start();
        triggersLog.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        triggersLog.detachAppender(logs);
    }

    // AC2

    @Test
    void onDocumentAccepted_submitsOneTaskThatFetchesTheEventsDocument() {
        triggers.onDocumentAccepted(makeEvent());

        assertThat(submitted).hasSize(1);
        verifyNoInteractions(fetcher);

        submitted.getFirst().run();

        verify(fetcher).fetchFor(KB, DOCUMENT, USER);
    }

    // AC3

    @Test
    void aFailingFetch_isLoggedAndNotRethrown() {
        doThrow(new IllegalStateException("secret detail")).when(fetcher).fetchFor(any(), any(), any());
        new ArticleFetchTriggers(fetcher, Runnable::run).onDocumentAccepted(makeEvent());

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                .contains(KB.toString(), DOCUMENT.toString(), "IllegalStateException")
                .doesNotContain("secret detail");
        });
    }

    @Test
    void aFailingFetch_doesNotEscapeTheTask() {
        doThrow(new RuntimeException()).when(fetcher).fetchFor(any(), any(), any());
        triggers.onDocumentAccepted(makeEvent());

        assertThatNoException().isThrownBy(() -> submitted.getFirst().run());
    }

    // AC4

    @Test
    void onDocumentAccepted_runsOnlyAfterTheUploadCommits() throws NoSuchMethodException {
        TransactionalEventListener listener = ArticleFetchTriggers.class
            .getMethod("onDocumentAccepted", DomainEvent.DocumentAccepted.class)
            .getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(listener.fallbackExecution()).isFalse();
    }

    private static DomainEvent.DocumentAccepted makeEvent() {
        return new DomainEvent.DocumentAccepted(DOCUMENT, KB, USER, UploadSource.API, Instant.EPOCH);
    }
}
