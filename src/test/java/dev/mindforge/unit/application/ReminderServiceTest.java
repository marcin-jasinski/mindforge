package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mindforge.application.service.FlashcardService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.ReminderService;
import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.CardType;
import dev.mindforge.domain.model.ExternalIdentity;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.domain.port.ReminderSender;

class ReminderServiceTest {

    private static final String CARD_FRONT = "What is mitosis?";

    private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
    private final KnowledgeBaseService knowledgeBases = mock(KnowledgeBaseService.class);
    private final FlashcardService flashcards = mock(FlashcardService.class);
    private final RecordingSender sender = new RecordingSender();
    private final ReminderService service = new ReminderService(identities, knowledgeBases, flashcards, sender);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger serviceLog = (Logger) LoggerFactory.getLogger(ReminderService.class);

    ReminderServiceTest() {
        logs.start();
        serviceLog.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        serviceLog.detachAppender(logs);
    }

    // AC1, AC2

    @Test
    void aUserWithDueCardsInTwoKnowledgeBases_getsOneDmWithOneLinePerBaseOrderedByName_omittingZero() {
        ExternalIdentity user = makeIdentity("111");
        KnowledgeBase zoology = makeKnowledgeBase(user, "Zoology");
        KnowledgeBase biology = makeKnowledgeBase(user, "Biology");
        KnowledgeBase chemistry = makeKnowledgeBase(user, "Chemistry");
        givenIdentities(user);
        givenKnowledgeBases(user, zoology, biology, chemistry);
        givenDue(zoology, 3);
        givenDue(biology, 12);
        givenDue(chemistry, 0);

        service.sendDueReminders();

        assertThat(sender.sent).containsExactly(new Sent("111",
            "You have 12 flashcards due in *Biology*\nYou have 3 flashcards due in *Zoology*"));
        verify(identities).findByPlatform(ExternalPlatform.DISCORD);
        verify(knowledgeBases).list(user.userId());
        verify(flashcards).due(eq(biology.kbId()), any(StudyScope.WholeKnowledgeBase.class));
        verify(flashcards).due(eq(chemistry.kbId()), any(StudyScope.WholeKnowledgeBase.class));
    }

    // AC3

    @Test
    void aUserWithZeroDueCards_getsNoDm() {
        ExternalIdentity user = makeIdentity("111");
        KnowledgeBase biology = makeKnowledgeBase(user, "Biology");
        givenIdentities(user);
        givenKnowledgeBases(user, biology);
        givenDue(biology, 0);

        service.sendDueReminders();

        assertThat(sender.sent).isEmpty();
    }

    @Test
    void aUserWithNoKnowledgeBases_getsNoDm() {
        ExternalIdentity user = makeIdentity("111");
        givenIdentities(user);
        givenKnowledgeBases(user);

        service.sendDueReminders();

        assertThat(sender.sent).isEmpty();
    }

    // AC4

    @Test
    void aFailedSendForUser1_stillRemindsUser2_andLogsOneWarnWithUser1sIdAndExceptionClass() {
        ExternalIdentity first = makeIdentity("111");
        ExternalIdentity second = makeIdentity("222");
        KnowledgeBase firstBase = makeKnowledgeBase(first, "Biology");
        KnowledgeBase secondBase = makeKnowledgeBase(second, "Physics");
        givenIdentities(first, second);
        givenKnowledgeBases(first, firstBase);
        givenKnowledgeBases(second, secondBase);
        givenDue(firstBase, 1);
        givenDue(secondBase, 2);
        sender.failFor = "111";

        service.sendDueReminders();

        assertThat(sender.sent).containsExactly(new Sent("222", "You have 2 flashcards due in *Physics*"));
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                .contains(first.userId().toString(), "IllegalStateException")
                .doesNotContain("blocked", "Biology");
        });
    }

    @Test
    void aFailedCountForUser1_stillRemindsUser2() {
        ExternalIdentity first = makeIdentity("111");
        ExternalIdentity second = makeIdentity("222");
        KnowledgeBase firstBase = makeKnowledgeBase(first, "Biology");
        KnowledgeBase secondBase = makeKnowledgeBase(second, "Physics");
        givenIdentities(first, second);
        givenKnowledgeBases(first, firstBase);
        givenKnowledgeBases(second, secondBase);
        when(flashcards.due(eq(firstBase.kbId()), any())).thenThrow(new IllegalArgumentException("db down"));
        givenDue(secondBase, 2);

        service.sendDueReminders();

        assertThat(sender.sent).extracting(Sent::externalUserId).containsExactly("222");
        assertThat(logs.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
            .contains(first.userId().toString(), "IllegalArgumentException"));
    }

    // AC5

    @Test
    void neverGeneratesCards_andTheDmContainsNoCardFront() {
        ExternalIdentity user = makeIdentity("111");
        KnowledgeBase biology = makeKnowledgeBase(user, "Biology");
        givenIdentities(user);
        givenKnowledgeBases(user, biology);
        givenDue(biology, 2);

        service.sendDueReminders();

        verify(flashcards, never()).deck(any(), any());
        verify(flashcards, never()).refresh(any(), any());
        assertThat(sender.sent).singleElement().satisfies(sent -> assertThat(sent.text()).doesNotContain(CARD_FRONT));
    }

    // AC6

    @Test
    void aDmOver2000Chars_isTruncatedEndingWithAndNMore() {
        ExternalIdentity user = makeIdentity("111");
        List<KnowledgeBase> bases = IntStream.range(0, 100)
            .mapToObj(i -> makeKnowledgeBase(user, "Knowledge base %03d".formatted(i))).toList();
        givenIdentities(user);
        givenKnowledgeBases(user, bases.toArray(KnowledgeBase[]::new));
        bases.forEach(base -> givenDue(base, 5));

        service.sendDueReminders();

        assertThat(sender.sent).singleElement().satisfies(sent -> {
            assertThat(sent.text()).hasSizeLessThanOrEqualTo(2000).startsWith(
                "You have 5 flashcards due in *Knowledge base 000*\n").matches("(?s).*\n…and \\d+ more");
            String[] lines = sent.text().split("\n");
            int kept = lines.length - 1;
            assertThat(lines[lines.length - 1]).isEqualTo("…and " + (100 - kept) + " more");
        });
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void givenIdentities(ExternalIdentity... linked) {
        when(identities.findByPlatform(ExternalPlatform.DISCORD)).thenReturn(List.of(linked));
    }

    private void givenKnowledgeBases(ExternalIdentity user, KnowledgeBase... owned) {
        when(knowledgeBases.list(user.userId())).thenReturn(List.of(owned));
    }

    private void givenDue(KnowledgeBase knowledgeBase, int count) {
        List<CardState> due = IntStream.range(0, count).mapToObj(i -> makeCard()).toList();
        when(flashcards.due(eq(knowledgeBase.kbId()), any(StudyScope.WholeKnowledgeBase.class))).thenReturn(due);
    }

    private static ExternalIdentity makeIdentity(String externalId) {
        return new ExternalIdentity(ExternalPlatform.DISCORD, externalId, UUID.randomUUID());
    }

    private static KnowledgeBase makeKnowledgeBase(ExternalIdentity owner, String name) {
        return new KnowledgeBase(UUID.randomUUID(), owner.userId(), name, "", Instant.EPOCH, 0, 0);
    }

    private static CardState makeCard() {
        Flashcard card = new Flashcard(UUID.randomUUID().toString(), UUID.randomUUID(), "phase", CardType.BASIC,
            CARD_FRONT, "Cell division.", "hash");
        return new CardState(card, 2.5, 0, 0, Instant.EPOCH, null);
    }

    private record Sent(String externalUserId, String text) {}

    private static final class RecordingSender implements ReminderSender {

        private final List<Sent> sent = new ArrayList<>();
        private String failFor;

        @Override
        public void send(String externalUserId, String text) {
            if (externalUserId.equals(failFor)) {
                throw new IllegalStateException("blocked");
            }
            sent.add(new Sent(externalUserId, text));
        }
    }
}
