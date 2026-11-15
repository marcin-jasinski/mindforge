package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.domain.model.ExternalIdentityConflictException;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.domain.port.KnowledgeBaseRepository;

class ExternalIdentityServiceTest {

    private static final String EXTERNAL_ID = "123456789012345678";

    private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
    private final KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);

    @Test
    void firstContact_provisionsAUserWithTheSyntheticEmailAndADiscordKnowledgeBase() {
        when(identities.findUserId(ExternalPlatform.DISCORD, EXTERNAL_ID)).thenReturn(Optional.empty());
        when(identities.createUserAndLink(eq(ExternalPlatform.DISCORD), eq(EXTERNAL_ID), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));

        UUID userId = makeService().resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "  Ada\nLovelace ");

        User user = capturedUser();
        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.email()).isEqualTo("discord-" + EXTERNAL_ID + "@users.invalid");
        assertThat(user.passwordHash()).isNull();
        assertThat(user.avatarUrl()).isNull();
        assertThat(user.displayName()).isEqualTo("Ada Lovelace");
        ArgumentCaptor<KnowledgeBase> knowledgeBase = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBases).insert(knowledgeBase.capture());
        assertThat(knowledgeBase.getValue().ownerId()).isEqualTo(userId);
        assertThat(knowledgeBase.getValue().name()).isEqualTo("Discord");
    }

    @Test
    void firstContact_writesTheUserAndTheKnowledgeBaseInOneTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean();
        TransactionOperations recording = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                inTransaction.set(true);
                try {
                    return action.doInTransaction(null);
                } finally {
                    inTransaction.set(false);
                }
            }
        };
        when(identities.findUserId(ExternalPlatform.DISCORD, EXTERNAL_ID)).thenReturn(Optional.empty());
        when(identities.createUserAndLink(eq(ExternalPlatform.DISCORD), eq(EXTERNAL_ID), any()))
            .thenAnswer(invocation -> {
                assertThat(inTransaction).isTrue();
                return invocation.getArgument(2);
            });
        when(knowledgeBases.insert(any())).thenAnswer(invocation -> {
            assertThat(inTransaction).isTrue();
            return invocation.getArgument(0);
        });

        new ExternalIdentityService(identities, knowledgeBases, recording)
            .resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "Ada");

        verify(knowledgeBases).insert(any());
    }

    @Test
    void secondContact_returnsTheLinkedUserAndInsertsNothing() {
        UUID linked = UUID.randomUUID();
        when(identities.findUserId(ExternalPlatform.DISCORD, EXTERNAL_ID)).thenReturn(Optional.of(linked));

        UUID userId = makeService().resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "Ada");

        assertThat(userId).isEqualTo(linked);
        verify(identities, never()).createUserAndLink(any(), any(), any());
        verifyNoInteractions(knowledgeBases);
    }

    @Test
    void blankDisplayName_fallsBackToTheExternalId() {
        givenFirstContact();

        makeService().resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, " \n\t ");

        assertThat(capturedUser().displayName()).isEqualTo(EXTERNAL_ID);
    }

    @Test
    void nullDisplayName_fallsBackToTheExternalId() {
        givenFirstContact();

        makeService().resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, null);

        assertThat(capturedUser().displayName()).isEqualTo(EXTERNAL_ID);
    }

    @Test
    void multiLineOverLongDisplayName_isMadeSingleLineAndCutTo255Chars() {
        givenFirstContact();

        makeService().resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "line one\r\nline two " + "x".repeat(300));

        String displayName = capturedUser().displayName();
        assertThat(displayName).startsWith("line one line two x").hasSize(255).doesNotContain("\n", "\r");
    }

    @Test
    void lostRace_reReadsTheLinkedUserOnceAndInsertsNoKnowledgeBase() {
        UUID winner = UUID.randomUUID();
        when(identities.findUserId(ExternalPlatform.DISCORD, EXTERNAL_ID))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(winner));
        when(identities.createUserAndLink(eq(ExternalPlatform.DISCORD), eq(EXTERNAL_ID), any()))
            .thenThrow(new ExternalIdentityConflictException(ExternalPlatform.DISCORD));

        UUID userId = makeService().resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "Ada");

        assertThat(userId).isEqualTo(winner);
        verify(identities, times(2)).findUserId(ExternalPlatform.DISCORD, EXTERNAL_ID);
        verifyNoInteractions(knowledgeBases);
    }

    @Test
    void conflictWithNoLinkedUser_isRethrown() {
        when(identities.findUserId(ExternalPlatform.DISCORD, EXTERNAL_ID)).thenReturn(Optional.empty());
        ExternalIdentityConflictException conflict = new ExternalIdentityConflictException(ExternalPlatform.DISCORD);
        when(identities.createUserAndLink(eq(ExternalPlatform.DISCORD), eq(EXTERNAL_ID), any())).thenThrow(conflict);

        assertThatExceptionOfType(ExternalIdentityConflictException.class)
            .isThrownBy(() -> makeService().resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "Ada"))
            .isSameAs(conflict);
        verify(identities, times(2)).findUserId(ExternalPlatform.DISCORD, EXTERNAL_ID);
        verifyNoInteractions(knowledgeBases);
    }

    @Test
    void firstSlackContact_provisionsAUserWithTheSlackSyntheticEmailAndASlackKnowledgeBase() {
        when(identities.findUserId(ExternalPlatform.SLACK, "T1-U1")).thenReturn(Optional.empty());
        when(identities.createUserAndLink(eq(ExternalPlatform.SLACK), eq("T1-U1"), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));

        UUID userId = makeService().resolve(ExternalPlatform.SLACK, "T1-U1", "Ada");

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(identities).createUserAndLink(eq(ExternalPlatform.SLACK), eq("T1-U1"), user.capture());
        assertThat(user.getValue().userId()).isEqualTo(userId);
        assertThat(user.getValue().email()).isEqualTo("slack-t1-u1@users.invalid");
        assertThat(user.getValue().passwordHash()).isNull();
        ArgumentCaptor<KnowledgeBase> knowledgeBase = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBases).insert(knowledgeBase.capture());
        assertThat(knowledgeBase.getValue().ownerId()).isEqualTo(userId);
        assertThat(knowledgeBase.getValue().name()).isEqualTo("Slack");
    }

    @Test
    void secondSlackContact_returnsTheLinkedUser() {
        UUID linked = UUID.randomUUID();
        when(identities.findUserId(ExternalPlatform.SLACK, "T1-U1")).thenReturn(Optional.of(linked));

        assertThat(makeService().resolve(ExternalPlatform.SLACK, "T1-U1", "Ada")).isEqualTo(linked);
        verify(identities, never()).createUserAndLink(any(), any(), any());
        verifyNoInteractions(knowledgeBases);
    }

    @Test
    void sameSlackUserIdUnderTwoTeams_resolvesToTwoUsers() {
        when(identities.findUserId(eq(ExternalPlatform.SLACK), any())).thenReturn(Optional.empty());
        when(identities.createUserAndLink(eq(ExternalPlatform.SLACK), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));

        UUID first = makeService().resolve(ExternalPlatform.SLACK, "T1-U1", "Ada");
        UUID second = makeService().resolve(ExternalPlatform.SLACK, "T2-U1", "Ada");

        assertThat(second).isNotEqualTo(first);
        verify(identities).createUserAndLink(eq(ExternalPlatform.SLACK), eq("T1-U1"), any());
        verify(identities).createUserAndLink(eq(ExternalPlatform.SLACK), eq("T2-U1"), any());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private ExternalIdentityService makeService() {
        return new ExternalIdentityService(identities, knowledgeBases, TransactionOperations.withoutTransaction());
    }

    private void givenFirstContact() {
        when(identities.findUserId(ExternalPlatform.DISCORD, EXTERNAL_ID)).thenReturn(Optional.empty());
        when(identities.createUserAndLink(eq(ExternalPlatform.DISCORD), eq(EXTERNAL_ID), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));
    }

    private User capturedUser() {
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(identities).createUserAndLink(eq(ExternalPlatform.DISCORD), eq(EXTERNAL_ID), user.capture());
        return user.getValue();
    }
}
