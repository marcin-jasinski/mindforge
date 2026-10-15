package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A started quiz: how many questions it has, never the questions' answers. */
public record QuizSessionResponse(UUID sessionId, int questionCount, Instant expiresAt) {}
