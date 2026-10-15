package dev.mindforge.domain.model;

import java.util.UUID;

/** What a study session draws from: the whole knowledge base, one lesson, or one page with its linked pages. */
public sealed interface StudyScope permits StudyScope.WholeKnowledgeBase, StudyScope.Lesson, StudyScope.Page {

    record WholeKnowledgeBase() implements StudyScope {}

    /** The reserved {@code conversation} lesson is never offered. */
    record Lesson(String lessonId) implements StudyScope {}

    record Page(UUID pageId) implements StudyScope {}
}
