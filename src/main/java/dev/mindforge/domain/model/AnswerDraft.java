package dev.mindforge.domain.model;

import java.util.List;

/** An answer as the writer returns it, with the page paths it claims to cite; code keeps only paths it was given. */
public record AnswerDraft(String answer, List<String> citedPaths) {

    public AnswerDraft {
        citedPaths = citedPaths == null ? List.of() : List.copyOf(citedPaths);
    }
}
