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
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.support.TestFixtures;

class KnowledgeBaseServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    private final KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
    private final QuizSessionStore quizSessions = mock(QuizSessionStore.class);

    @Test
    void shouldRefuseAKnowledgeBaseToAnyoneButItsOwner() {
        KnowledgeBase knowledgeBase = givenKnowledgeBase();
        KnowledgeBaseService service = new KnowledgeBaseService(knowledgeBases, quizSessions);

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
            .isThrownBy(() -> new KnowledgeBaseService(knowledgeBases, quizSessions)
                .requireOwner(UUID.randomUUID(), OWNER));
    }

    @Test
    void shouldRefuseToDeleteAKnowledgeBaseWhileARunIsActive() {
        KnowledgeBase knowledgeBase = givenKnowledgeBase();
        when(knowledgeBases.deleteIfIdle(knowledgeBase.kbId())).thenReturn(false);

        assertThatExceptionOfType(KnowledgeBaseBusyException.class)
            .isThrownBy(() -> new KnowledgeBaseService(knowledgeBases, quizSessions)
                .delete(knowledgeBase.kbId(), OWNER));
    }

    @Test
    void shouldDeleteAnIdleKnowledgeBase() {
        KnowledgeBase knowledgeBase = givenKnowledgeBase();
        when(knowledgeBases.deleteIfIdle(knowledgeBase.kbId())).thenReturn(true);

        new KnowledgeBaseService(knowledgeBases, quizSessions).delete(knowledgeBase.kbId(), OWNER);

        verify(knowledgeBases).deleteIfIdle(knowledgeBase.kbId());
        verify(quizSessions).forgetKnowledgeBase(knowledgeBase.kbId());
    }

    @Test
    void shouldKeepTheCachedQuizSessionsOfABusyKnowledgeBase() {
        KnowledgeBase knowledgeBase = givenKnowledgeBase();
        when(knowledgeBases.deleteIfIdle(knowledgeBase.kbId())).thenReturn(false);

        KnowledgeBaseService service = new KnowledgeBaseService(knowledgeBases, quizSessions);
        assertThatExceptionOfType(KnowledgeBaseBusyException.class)
            .isThrownBy(() -> service.delete(knowledgeBase.kbId(), OWNER));
        verify(quizSessions, never()).forgetKnowledgeBase(any());
    }

    private KnowledgeBase givenKnowledgeBase() {
        KnowledgeBase knowledgeBase = TestFixtures.makeKnowledgeBase(null, OWNER);
        when(knowledgeBases.findById(any())).thenReturn(Optional.empty());
        when(knowledgeBases.findById(knowledgeBase.kbId())).thenReturn(Optional.of(knowledgeBase));
        return knowledgeBase;
    }
}
