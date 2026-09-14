package dev.mindforge.domain.model;

/** A level-1 section of a live Concept, shown to Supersede as something a run's claims might correct. */
public record CandidateSection(String path, String anchor, String heading, String text) {}
