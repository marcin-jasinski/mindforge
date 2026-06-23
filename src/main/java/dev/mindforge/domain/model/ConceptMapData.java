package dev.mindforge.domain.model;

import java.util.List;

/**
 * The concept-mapper agent output: a graph of concept nodes and the typed
 * relationships between them. Source for the Neo4j projection.
 */
public record ConceptMapData(
    List<ConceptNode> nodes,
    List<ConceptEdge> edges
) {

    public ConceptMapData {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /** A single concept within a concept map. */
    public record ConceptNode(String id, String label) {}

    /** A directed, typed relationship between two concept nodes. */
    public record ConceptEdge(String sourceId, String targetId, String relation) {}
}
