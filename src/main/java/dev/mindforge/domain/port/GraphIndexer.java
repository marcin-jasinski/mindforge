package dev.mindforge.domain.port;

import java.util.UUID;

import dev.mindforge.domain.model.DocumentArtifact;

/** Port for projecting concept maps into the derived graph store (Neo4j). */
public interface GraphIndexer {

    void indexArtifact(DocumentArtifact artifact);

    void removeByLesson(UUID knowledgeBaseId, String lessonId);
}
