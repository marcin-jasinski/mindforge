package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.slack.SlackKnowledgeBases;

class SlackKnowledgeBasesTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String NOT_ONE = "This command needs exactly one knowledge base. "
        + "Selecting a knowledge base is not available in Slack yet.";
    private static final String SECRET = "kb 1234 was deleted";

    private final KnowledgeBaseService service = mock(KnowledgeBaseService.class);
    private final SlackKnowledgeBases knowledgeBases = new SlackKnowledgeBases(service);
    private final RecordingSlackReply reply = new RecordingSlackReply();

    // AC1

    @Test
    void theOnlyOwnedKnowledgeBase_resolvesToItsIdAfterTheOwnershipCheck() {
        KnowledgeBase kb = makeKnowledgeBase();
        when(service.list(USER_ID)).thenReturn(List.of(kb));

        assertThat(knowledgeBases.resolveKnowledgeBase(USER_ID, reply)).contains(kb.kbId());

        verify(service).requireOwner(kb.kbId(), USER_ID);
        assertThat(reply.events).isEmpty();
    }

    @Test
    void noKnowledgeBase_givesTheFixedReplyAndNoId() {
        when(service.list(USER_ID)).thenReturn(List.of());

        assertThat(knowledgeBases.resolveKnowledgeBase(USER_ID, reply)).isEmpty();

        assertThat(reply.events).containsExactly(NOT_ONE);
        verify(service, never()).requireOwner(any(), any());
    }

    @Test
    void twoKnowledgeBases_giveTheFixedReplyWithoutAnOwnershipCheck() {
        when(service.list(USER_ID)).thenReturn(List.of(makeKnowledgeBase(), makeKnowledgeBase()));

        assertThat(knowledgeBases.resolveKnowledgeBase(USER_ID, reply)).isEmpty();

        assertThat(reply.events).containsExactly(NOT_ONE);
        verify(service, never()).requireOwner(any(), any());
    }

    static List<RuntimeException> ownershipFailures() {
        return List.of(new NotFoundException(SECRET), new NotOwnerException(UUID.randomUUID()));
    }

    @ParameterizedTest
    @MethodSource("ownershipFailures")
    void aFailedOwnershipCheck_givesTheFixedReplyWithoutTheMessage(RuntimeException failure) {
        KnowledgeBase kb = makeKnowledgeBase();
        when(service.list(USER_ID)).thenReturn(List.of(kb));
        doThrow(failure).when(service).requireOwner(kb.kbId(), USER_ID);

        assertThat(knowledgeBases.resolveKnowledgeBase(USER_ID, reply)).isEmpty();

        assertThat(reply.events).containsExactly(NOT_ONE);
    }

    private static KnowledgeBase makeKnowledgeBase() {
        return new KnowledgeBase(UUID.randomUUID(), USER_ID, "Biology", null, null, 0, 0);
    }
}
