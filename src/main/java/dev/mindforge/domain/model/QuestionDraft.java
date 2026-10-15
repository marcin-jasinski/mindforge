package dev.mindforge.domain.model;

/** A quiz question as the generator proposes it, naming the page it was cut from. */
public record QuestionDraft(String pagePath, String sectionAnchor, String question, String referenceAnswer,
                            String groundingExcerpt) {}
