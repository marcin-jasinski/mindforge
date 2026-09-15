package dev.mindforge.domain.model;

import java.util.UUID;

/**
 * A front/back recall item cut from one section of one Concept page (ADR 0018). Its id is its content, so an unchanged
 * card keeps its schedule through a rewrite; {@code sourceHash} is the page content it was generated or reused from.
 */
public record Flashcard(
    String cardId,
    UUID pageId,
    String sectionAnchor,
    CardType cardType,
    String front,
    String back,
    String sourceHash
) {

    private static final int ID_LENGTH = 16;

    /** {@code sha256(kbId|pageId|cardType|front|back)[:16]}. */
    public static String computeCardId(UUID kbId, UUID pageId, CardType cardType, String front, String back) {
        return Hashes.sha256Hex(kbId + "|" + pageId + "|" + cardType + "|" + front + "|" + back).substring(0, ID_LENGTH);
    }

    /** {@code sha256(title + "\n" + stripLinks(body))[:16]}: a link-only or supersession-only change keeps it (T21). */
    public static String sourceHash(String title, String body) {
        return Hashes.sha256Hex(title + "\n" + MarkdownStructure.stripLinks(body)).substring(0, ID_LENGTH);
    }
}
