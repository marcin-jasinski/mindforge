package dev.mindforge.api.dto.request;

import java.util.UUID;

/** The study scope: a page and its linked pages, else a lesson, else the whole knowledge base. */
public record QuizSessionRequest(String lessonId, UUID pageId) {}
