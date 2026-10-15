package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A question, its answer and the pages the answer was grounded on. */
public record InteractionTurn(UUID turnId, String question, String answer, List<String> usedPagePaths,
                              Instant createdAt) {

    public InteractionTurn {
        usedPagePaths = List.copyOf(usedPagePaths);
    }
}
