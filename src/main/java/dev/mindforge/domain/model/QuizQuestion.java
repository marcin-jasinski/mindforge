package dev.mindforge.domain.model;

import java.util.UUID;

/**
 * One generated question. {@code referenceAnswer} and {@code groundingExcerpt} live only in the server-side session
 * row and never reach a response, a page or an export.
 */
public record QuizQuestion(
    UUID pageId,
    String sectionAnchor,
    String question,
    String referenceAnswer,
    String groundingExcerpt
) {}
