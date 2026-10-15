package dev.mindforge.domain.model;

/** A section of a page being rewritten whose claims another page has corrected, shown to the writer beside the body. */
public record SupersededSection(String heading, String anchor, String supersedingPath, String supersedingTitle) {}
