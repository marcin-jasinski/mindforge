package dev.mindforge.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Request body for uploading a document into a knowledge base via the API. */
public record DocumentUploadRequest(
    @NotNull UUID knowledgeBaseId
) {}
