package dev.mindforge.domain.model;

/** A proposal to wrap a phrase already in a page in a link to another page, optionally to one of its sections. */
public record LinkInsertion(String pagePath, String phrase, String targetPath, String fragment) {}
