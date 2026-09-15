package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.RevertService;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.RevertNotAllowedException;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.domain.port.WikiStore;

class RevertServiceTest {

    private static final UUID KB = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();
    private static final UUID LATER_RUN = UUID.randomUUID();
    private static final UUID EARLIER_RUN = UUID.randomUUID();

    private final WikiStore wiki = mock(WikiStore.class);
    private final IngestRunRepository runs = makeRuns();

    @Test
    void shouldRestoreEveryPageTheRunStillTipsAndLeaveThePagesALaterRunTouched() {
        UUID tipped = UUID.randomUUID();
        UUID touchedLater = UUID.randomUUID();
        givenRun(RunKind.INGEST);
        when(wiki.revisionsByRun(KB, RUN)).thenReturn(List.of(
            makeRevision(tipped, 3, RUN, "po"), makeRevision(touchedLater, 2, RUN, "po")));
        when(wiki.tipRevisions(eq(KB), any())).thenReturn(Map.of(
            tipped, makeRevision(tipped, 3, RUN, "po"), touchedLater, makeRevision(touchedLater, 3, LATER_RUN, "x")));
        when(wiki.findRevision(KB, tipped, 2)).thenReturn(Optional.of(makeRevision(tipped, 2, EARLIER_RUN, "przed")));
        when(wiki.deleteSupersessionsBySuperseding(KB, RUN, List.of(tipped))).thenReturn(1);

        UUID revertRunId = makeService().revert(KB, RUN);

        verify(wiki).savePage(KB, new PageWrite(tipped, "concepts/" + tipped, "Tytuł przed", "Opis przed",
            PageType.CONCEPT, "przed"), revertRunId);
        verify(wiki).savePage(any(), any(), any());
        verify(wiki).deleteSources(KB, RUN, List.of(tipped));
        verify(runs).complete(KB, revertRunId, 1, false, List.of(), Map.of());
    }

    @Test
    void shouldTombstoneAPageTheRunCreated() {
        UUID created = UUID.randomUUID();
        givenRun(RunKind.INGEST);
        givenOnlyRevision(makeRevision(created, 1, RUN, "nowa"));

        UUID revertRunId = makeService().revert(KB, RUN);

        verify(wiki).deletePage(KB, created, revertRunId);
    }

    @Test
    void shouldReinsertAPageTheRunDeletedWhileItsPathIsFree() {
        UUID deleted = UUID.randomUUID();
        PageRevision before = makeRevision(deleted, 1, EARLIER_RUN, "treść");
        givenRun(RunKind.INGEST);
        givenOnlyRevision(makeRevision(deleted, 2, RUN, null));
        when(wiki.findRevision(KB, deleted, 1)).thenReturn(Optional.of(before));

        UUID revertRunId = makeService().revert(KB, RUN);

        verify(wiki).reinsertPage(KB, before, revertRunId);
    }

    @Test
    void shouldNotOfferRevertOfADeletionWhosePathANewPageTook() {
        UUID deleted = UUID.randomUUID();
        givenRun(RunKind.INGEST);
        givenOnlyRevision(makeRevision(deleted, 2, RUN, null));
        when(wiki.findRevision(KB, deleted, 1)).thenReturn(Optional.of(makeRevision(deleted, 1, EARLIER_RUN, "t")));
        when(wiki.findByPath(KB, "concepts/" + deleted)).thenReturn(Optional.of(mock(WikiPage.class)));

        assertThatExceptionOfType(RevertNotAllowedException.class).isThrownBy(() -> makeService().revert(KB, RUN));
        verify(wiki, never()).reinsertPage(any(), any(), any());
        verify(runs, never()).complete(any(), any(), anyInt(), anyBoolean(), any(), any());
    }

    @Test
    void shouldOfferRevertOnlyForACompletedIngestThatStillTipsAPage() {
        givenRun(RunKind.INGEST);
        givenOnlyRevision(makeRevision(UUID.randomUUID(), 1, RUN, "nowa"));
        assertThat(makeService().isOffered(KB, RUN)).isTrue();

        givenRun(RunKind.REVERT);
        assertThat(makeService().isOffered(KB, RUN)).isFalse();
        verify(runs, never()).enqueue(any(), any());
    }

    @Test
    void shouldNotRevertARevertRun() {
        givenRun(RunKind.REVERT);

        assertThatExceptionOfType(RevertNotAllowedException.class).isThrownBy(() -> makeService().revert(KB, RUN));
        verify(wiki, never()).revisionsByRun(any(), any());
    }

    @Test
    void shouldRefuseWhileAnotherRunHoldsTheLease() {
        when(runs.claim(eq(KB), any())).thenReturn(false);

        assertThatExceptionOfType(KnowledgeBaseBusyException.class).isThrownBy(() -> makeService().revert(KB, RUN));
        verify(runs, never()).findById(any(), any());
        verify(wiki, never()).revisionsByRun(any(), any());
    }

    @Test
    void shouldRemoveOneSupersessionAsARevertRunOfTheRunThatInsertedItAfterClaimingTheLease() {
        UUID supersessionId = givenSupersession();
        when(wiki.deleteSupersession(KB, supersessionId)).thenReturn(true);

        UUID revertRunId = makeService().removeSupersession(KB, supersessionId);

        ArgumentCaptor<IngestRun> enqueued = ArgumentCaptor.forClass(IngestRun.class);
        verify(runs).enqueue(eq(KB), enqueued.capture());
        assertThat(enqueued.getValue()).extracting(IngestRun::runId, IngestRun::kind, IngestRun::revertsRunId)
            .containsExactly(revertRunId, RunKind.REVERT, EARLIER_RUN);
        InOrder order = inOrder(runs, wiki);
        order.verify(runs).claim(KB, revertRunId);
        order.verify(wiki).deleteSupersession(KB, supersessionId);
        order.verify(runs).complete(KB, revertRunId, 1, false, List.of(), Map.of());
    }

    @Test
    void shouldNotRemoveASupersessionThatWasRemovedWhileWaitingForTheLease() {
        UUID supersessionId = givenSupersession();

        assertThatExceptionOfType(RevertNotAllowedException.class)
            .isThrownBy(() -> makeService().removeSupersession(KB, supersessionId));
        verify(runs, never()).complete(any(), any(), anyInt(), anyBoolean(), any(), any());
    }

    private UUID givenSupersession() {
        UUID supersessionId = UUID.randomUUID();
        when(wiki.findSupersession(KB, supersessionId)).thenReturn(Optional.of(new PageSupersession(
            supersessionId, UUID.randomUUID(), "faza", UUID.randomUUID(), EARLIER_RUN, Instant.EPOCH)));
        return supersessionId;
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private RevertService makeService() {
        return new RevertService(runs, wiki, mock(ProgressNotifier.class), TransactionOperations.withoutTransaction());
    }

    private static IngestRunRepository makeRuns() {
        IngestRunRepository runs = mock(IngestRunRepository.class);
        when(runs.enqueue(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(runs.claim(any(), any())).thenReturn(true);
        return runs;
    }

    private void givenRun(RunKind kind) {
        when(runs.findById(KB, RUN)).thenReturn(Optional.of(new IngestRun(RUN, KB, kind, null, null,
            RunStatus.COMPLETED, 1, null, null, List.of(), false, 0, Map.of(), List.of(), Instant.EPOCH, null, null)));
    }

    private void givenOnlyRevision(PageRevision revision) {
        when(wiki.revisionsByRun(KB, RUN)).thenReturn(List.of(revision));
        when(wiki.tipRevisions(eq(KB), any())).thenReturn(Map.of(revision.pageId(), revision));
    }

    private static PageRevision makeRevision(UUID pageId, int revision, UUID runId, String body) {
        String label = body == null ? "usunięta" : body;
        return new PageRevision(pageId, revision, runId, "concepts/" + pageId, "Tytuł " + label, "Opis " + label,
            PageType.CONCEPT, body, Instant.EPOCH);
    }
}
