package dev.mindforge.api.controller;

import java.util.UUID;

import dev.mindforge.domain.model.StudyScope;

/** The study scope a request names: a page, else a lesson, else the whole knowledge base. */
final class StudyScopes {

    private StudyScopes() {}

    static StudyScope of(String lessonId, UUID pageId) {
        if (pageId != null) {
            return new StudyScope.Page(pageId);
        }
        return lessonId != null ? new StudyScope.Lesson(lessonId) : new StudyScope.WholeKnowledgeBase();
    }
}
