package dev.mindforge.domain.model;

/** What the writer returns for one task. There is no title: code owns titles. */
public record PageDraft(String description, String body) {}
