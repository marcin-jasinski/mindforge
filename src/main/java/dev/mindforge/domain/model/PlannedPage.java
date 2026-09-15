package dev.mindforge.domain.model;

/** A page an earlier chunk of this run will write, which a later chunk's claims may target. */
public record PlannedPage(String path, String title) {}
