package dev.mindforge.application.wiki;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.mindforge.domain.model.LinkInsertion;
import dev.mindforge.domain.model.MarkdownStructure;

/**
 * Applies link insertions to one body (ADR 0017, T26). An insertion wraps the first occurrence of its phrase that lies
 * wholly inside an eligible span, only when its target is one of the valid targets, is not the page itself, and any
 * fragment is a level-1 anchor of the target's body. The body with links stripped never changes.
 */
public final class LinkInsertionApplier {

    public record Result(String body, int applied, int dropped) {}

    private LinkInsertionApplier() {}

    /** @param targetBodies the body of every valid target, by path */
    public static Result apply(String pagePath, String body, List<LinkInsertion> insertions,
                               Map<String, String> targetBodies) {
        String current = body;
        int applied = 0;
        for (LinkInsertion insertion : insertions) {
            String next = insert(pagePath, current, insertion, targetBodies);
            if (next != null) {
                current = next;
                applied++;
            }
        }
        return new Result(current, applied, insertions.size() - applied);
    }

    private static String insert(String pagePath, String body, LinkInsertion insertion,
                                 Map<String, String> targetBodies) {
        String phrase = insertion.phrase();
        String target = insertion.targetPath();
        if (!pagePath.equals(insertion.pagePath()) || phrase == null || phrase.isBlank() || target == null
            || target.equals(pagePath) || phrase.contains("[") || phrase.contains("]") || phrase.contains("\n")) {
            return null;
        }
        String targetBody = targetBodies.get(target);
        if (targetBody == null || (insertion.fragment() != null
            && MarkdownStructure.section(targetBody, insertion.fragment()).isEmpty())) {
            return null;
        }
        for (MarkdownStructure.Span span : MarkdownStructure.eligibleSpans(body)) {
            int at = body.indexOf(phrase, span.start());
            if (at >= 0 && at + phrase.length() <= span.end()) {
                String link = "[" + phrase + "](/" + target + ".md"
                    + (insertion.fragment() == null ? "" : "#" + insertion.fragment()) + ")";
                String linked = body.substring(0, at) + link + body.substring(at + phrase.length());
                return Objects.equals(MarkdownStructure.stripLinks(linked), MarkdownStructure.stripLinks(body))
                    ? linked
                    : null;
            }
        }
        return null;
    }
}
