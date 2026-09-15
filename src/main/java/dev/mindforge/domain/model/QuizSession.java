package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A server-held quiz: its questions with their reference answers, and how far the learner got. */
public record QuizSession(
    UUID sessionId,
    UUID knowledgeBaseId,
    UUID userId,
    List<QuizQuestion> questions,
    int cursor,
    Instant expiresAt
) {

    public QuizSession {
        questions = List.copyOf(questions);
    }

    public boolean finished() {
        return cursor >= questions.size();
    }

    public QuizSession advanced() {
        return new QuizSession(sessionId, knowledgeBaseId, userId, questions, cursor + 1, expiresAt);
    }
}
