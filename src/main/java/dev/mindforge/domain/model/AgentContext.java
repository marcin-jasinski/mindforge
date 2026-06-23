package dev.mindforge.domain.model;

import java.util.UUID;

import dev.mindforge.domain.port.AIGateway;

/**
 * Everything an agent needs to do its work: the document being processed, the
 * artifact accumulated so far, the LLM gateway, and the run settings. Passed to
 * {@link Agent#execute(AgentContext)}.
 */
public record AgentContext(
    UUID documentId,
    UUID knowledgeBaseId,
    DocumentArtifact artifact,
    AIGateway gateway,
    ProcessingSettings settings
) {}
