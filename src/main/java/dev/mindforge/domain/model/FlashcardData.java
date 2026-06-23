package dev.mindforge.domain.model;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

/**
 * A single generated flashcard. The {@code cardId} is content-derived and stable:
 * the same card content within the same knowledge base and lesson always yields the
 * same id, which makes regeneration idempotent.
 */
public record FlashcardData(
    String cardId,
    CardType cardType,
    String front,
    String back
) {

    private static final int CARD_ID_LENGTH = 16;

    /** Builds a flashcard with a deterministic {@link #cardId()} derived from its content. */
    public static FlashcardData create(
        UUID kbId, String lessonId, CardType cardType, String front, String back) {
        return new FlashcardData(computeCardId(kbId, lessonId, cardType, front, back),
            cardType, front, back);
    }

    public static String computeCardId(
        UUID kbId, String lessonId, CardType cardType, String front, String back) {
        requireNonNull(kbId, "kbId");
        requireNonNull(lessonId, "lessonId");
        requireNonNull(cardType, "cardType");
        requireNonNull(front, "front");
        requireNonNull(back, "back");
        String material = kbId + "|" + lessonId + "|" + cardType + "|" + front + "|" + back;
        return Hashes.sha256Hex(material).substring(0, CARD_ID_LENGTH);
    }
}
