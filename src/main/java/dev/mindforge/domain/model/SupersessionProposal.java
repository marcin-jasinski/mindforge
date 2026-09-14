package dev.mindforge.domain.model;

/** A proposal that a section of one page is corrected by a page this run wrote; kept only if code verifies it. */
public record SupersessionProposal(String supersededPath, String sectionAnchor, String supersedingPath) {}
