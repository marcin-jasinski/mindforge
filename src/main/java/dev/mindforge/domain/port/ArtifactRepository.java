package dev.mindforge.domain.port;

import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.DocumentArtifact;

/** Persistence port for pipeline {@link DocumentArtifact} state and checkpoints. */
public interface ArtifactRepository {

    /** Persists the artifact with its current step checkpoints; returns the stored state. */
    DocumentArtifact saveCheckpoint(DocumentArtifact artifact);

    Optional<DocumentArtifact> loadLatest(UUID documentId);

    long countFlashcards(UUID knowledgeBaseId);
}
