package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.CardType;
import dev.mindforge.domain.model.FlashcardData;

class FlashcardDataTest {

    private static final UUID KB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void shouldComputeStableCardIdForSameContent() {
        FlashcardData first = FlashcardData.create(KB_ID, "lesson", CardType.BASIC, "Q", "A");
        FlashcardData second = FlashcardData.create(KB_ID, "lesson", CardType.BASIC, "Q", "A");

        assertThat(first.cardId()).isEqualTo(second.cardId()).hasSize(16);
    }

    @Test
    void shouldComputeDifferentCardIdForDifferentKnowledgeBase() {
        UUID otherKb = UUID.fromString("00000000-0000-0000-0000-000000000002");

        String a = FlashcardData.computeCardId(KB_ID, "lesson", CardType.BASIC, "Q", "A");
        String b = FlashcardData.computeCardId(otherKb, "lesson", CardType.BASIC, "Q", "A");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldComputeDifferentCardIdWhenAnyContentChanges() {
        String base = FlashcardData.computeCardId(KB_ID, "lesson", CardType.BASIC, "Q", "A");

        assertThat(FlashcardData.computeCardId(KB_ID, "other", CardType.BASIC, "Q", "A"))
            .isNotEqualTo(base);
        assertThat(FlashcardData.computeCardId(KB_ID, "lesson", CardType.CLOZE, "Q", "A"))
            .isNotEqualTo(base);
        assertThat(FlashcardData.computeCardId(KB_ID, "lesson", CardType.BASIC, "Q2", "A"))
            .isNotEqualTo(base);
        assertThat(FlashcardData.computeCardId(KB_ID, "lesson", CardType.BASIC, "Q", "A2"))
            .isNotEqualTo(base);
    }
}
