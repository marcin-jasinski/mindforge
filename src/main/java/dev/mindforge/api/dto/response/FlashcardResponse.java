package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import dev.mindforge.domain.model.CardType;

/** A due flashcard. */
public record FlashcardResponse(
    String cardId,
    UUID pageId,
    String sectionAnchor,
    CardType cardType,
    String front,
    String back,
    Instant dueAt
) {}
