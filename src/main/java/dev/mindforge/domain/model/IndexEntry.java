package dev.mindforge.domain.model;

/** One live page as the index lists it. */
public record IndexEntry(String path, String title, String description, PageType type) {}
