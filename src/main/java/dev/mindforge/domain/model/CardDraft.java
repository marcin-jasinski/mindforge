package dev.mindforge.domain.model;

/** A card as the generator proposes it; code gives it its id, page and hash. {@code sectionAnchor} may be null. */
public record CardDraft(CardType cardType, String front, String back, String sectionAnchor) {}
