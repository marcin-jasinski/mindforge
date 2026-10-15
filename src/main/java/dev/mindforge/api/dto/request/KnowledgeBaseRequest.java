package dev.mindforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for creating or renaming a knowledge base. */
public record KnowledgeBaseRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2000) String description
) {}
