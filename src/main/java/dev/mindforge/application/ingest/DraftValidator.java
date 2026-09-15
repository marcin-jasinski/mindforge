package dev.mindforge.application.ingest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.Identifier;
import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.PageDraft;
import dev.mindforge.domain.model.TextRules;

/** The checks a writer's draft must pass before it may be committed (T19, T21, T26), over {@link MarkdownStructure}. */
public final class DraftValidator {

    public static final int MAX_DESCRIPTION_LENGTH = 300;

    private static final String CITATIONS_ANCHOR = Identifier.slugify("Citations");
    private static final String FRONTMATTER_FENCE = "---\n";

    private DraftValidator() {}

    /** The description on one line and the body in its stored form. */
    public static PageDraft normalise(PageDraft draft) {
        return new PageDraft(TextRules.singleLine(draft.description()), TextRules.normaliseBody(draft.body()));
    }

    /**
     * Why a normalised draft fails its task, if it does.
     *
     * @param existingBody the live body a revision replaces, or null for a create
     * @param keepSections whether every level-1 anchor of the existing body must survive
     */
    public static Optional<String> problem(String title, PageDraft draft, String existingBody, boolean keepSections) {
        int descriptionLength = draft.description().codePointCount(0, draft.description().length());
        if (descriptionLength < 1 || descriptionLength > MAX_DESCRIPTION_LENGTH) {
            return Optional.of("description must be 1-" + MAX_DESCRIPTION_LENGTH + " characters");
        }
        String body = draft.body();
        if (body.isBlank()) {
            return Optional.of("empty body");
        }
        if (body.startsWith(FRONTMATTER_FENCE)) {
            return Optional.of("frontmatter");
        }
        Set<String> anchors = anchors(body);
        if (anchors.contains(CITATIONS_ANCHOR)) {
            return Optional.of("a # Citations section");
        }
        if (anchors.contains(Identifier.slugify(MarkdownStructure.stripLinks(title)))) {
            return Optional.of("a level-1 heading repeating the title");
        }
        Optional<MarkdownStructure.Link> invalid = MarkdownStructure.links(body).stream()
            .filter(link -> link.kind() == MarkdownStructure.LinkKind.INVALID)
            .findFirst();
        if (invalid.isPresent()) {
            return Optional.of("invalid link, image or reference definition: " + invalid.get().destination());
        }
        if (keepSections && existingBody != null) {
            List<String> dropped = anchors(existingBody).stream().filter(anchor -> !anchors.contains(anchor)).toList();
            if (!dropped.isEmpty()) {
                return Optional.of("dropped sections: " + String.join(", ", dropped));
            }
        }
        return Optional.empty();
    }

    private static Set<String> anchors(String body) {
        return MarkdownStructure.sections(body).stream()
            .map(MarkdownStructure.Section::anchor)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
