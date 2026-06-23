package dev.mindforge.domain.model;

import java.util.List;

/** The summarizer agent output: a prose summary plus extracted key points. */
public record SummaryData(
    String summary,
    List<String> keyPoints
) {

    public SummaryData {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
    }
}
