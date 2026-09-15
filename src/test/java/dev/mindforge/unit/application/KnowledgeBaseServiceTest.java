package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import dev.mindforge.support.TestFixtures;

class KnowledgeBaseServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    private final KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);

    @Test
    void shouldRefuseAKnowledgeBaseToAnyoneButItsOwner() {
        KnowledgeBase knowledgeBase = givenKnowledgeBase();
        KnowledgeBaseService service = new KnowledgeBaseService(knowledgeBases);

        assertThat(service.get(knowledgeBase.kbId(), OWNER)).isEqualTo(knowledgeBase);
        assertThatExceptionOfType(NotOwnerException.class)
            .isThrownBy(() -> service.get(knowledgeBase.kbId(), UUID.randomUUID()));
        assertThatExceptionOfType(NotOwnerException.class)
            .isThrownBy(() -> service.delete(knowledgeBase.kbId(), UUID.randomUUID()));
        verify(knowledgeBases, never()).deleteIfIdle(any());
    }

    @Test
    void shouldReportAMissingKnowledgeBaseAsNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
            .isThrownBy(() -> new KnowledgeBaseService(knowledgeBases).requireOwner(UUID.randomUUID(), OWNER));
    }

    @Test
    void shouldRefuseToDeleteAKnowledgeBaseWhileARunIsActive() {
        KnowledgeBase knowledgeBase = givenKnowledgeBase();

        assertThatExceptionOfType(KnowledgeBaseBusyException.class)
            .isThrownBy(() -> new KnowledgeBaseService(knowledgeBases).delete(knowledgeBase.kbId(), OWNER));
    }

    private KnowledgeBase givenKnowledgeBase() {
        KnowledgeBase knowledgeBase = TestFixtures.makeKnowledgeBase(null, OWNER);
        when(knowledgeBases.findById(any())).thenReturn(Optional.empty());
        when(knowledgeBases.findById(knowledgeBase.kbId())).thenReturn(Optional.of(knowledgeBase));
        return knowledgeBase;
    }
}
