package dev.mindforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionRequest(@NotBlank @Size(max = 4000) String question) {}
