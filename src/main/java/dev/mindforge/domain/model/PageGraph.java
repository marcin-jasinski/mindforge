package dev.mindforge.domain.model;

import java.util.List;

/** Every live page of a knowledge base and the distinct links between paths; a link's target may dangle. */
public record PageGraph(List<IndexEntry> pages, List<Edge> links) {

    public PageGraph {
        pages = List.copyOf(pages);
        links = List.copyOf(links);
    }

    public record Edge(String sourcePath, String targetPath) {}
}
