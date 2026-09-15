package dev.mindforge.application.wiki;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.SourceCitation;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.model.WikiPage;

/**
 * Renders a page as the SPA and export show it (T11): the stored body verbatim, a supersession note as a blockquote
 * directly under each superseded level-1 heading, and a projected {@code # Citations} section. The heading text is
 * never touched, so its anchor stays stable.
 */
public final class PageRenderer {

    private PageRenderer() {}

    public static String render(WikiPage page, List<LiveSupersession> supersessions, List<SourceCitation> sources) {
        return withCitations(withNotes(page.markdownBody(), supersessions), page.type(), sources);
    }

    /** Inserts {@code > Superseded by [title](/path.md).} under each superseded section's heading line. */
    public static String withNotes(String body, List<LiveSupersession> supersessions) {
        Map<Integer, List<String>> notesByHeadingEnd = new LinkedHashMap<>();
        for (LiveSupersession supersession : supersessions) {
            MarkdownStructure.section(body, supersession.sectionAnchor()).ifPresent(section -> {
                int newline = body.indexOf('\n', section.start());
                int headingEnd = newline < 0 ? body.length() : newline + 1;
                notesByHeadingEnd.computeIfAbsent(headingEnd, key -> new ArrayList<>())
                    .add("> Superseded by [" + TextRules.escapeLinkText(supersession.supersedingTitle()) + "](/"
                        + supersession.supersedingPath() + ".md).");
            });
        }
        StringBuilder rendered = new StringBuilder(body);
        notesByHeadingEnd.entrySet().stream()
            .sorted(Map.Entry.<Integer, List<String>>comparingByKey().reversed())
            .forEach(notes -> rendered.insert(notes.getKey(), (body.charAt(notes.getKey() - 1) == '\n' ? "" : "\n")
                + "\n" + String.join("\n", notes.getValue()) + "\n"));
        return rendered.toString();
    }

    /** The body without the sections these anchors name, so no study item is cut from a superseded claim. */
    public static String withoutSections(String body, Set<String> anchors) {
        StringBuilder kept = new StringBuilder();
        int position = 0;
        for (MarkdownStructure.Section section : MarkdownStructure.sections(body)) {
            if (anchors.contains(section.anchor())) {
                kept.append(body, position, section.start());
                position = section.end();
            }
        }
        return kept.append(body, position, body.length()).toString();
    }

    /**
     * Appends numbered citations: on a Concept, each contributing lesson linked to its Source Summary and each
     * conversation turn by date; on a Source Summary, each of its documents by filename and upload date.
     */
    public static String withCitations(String body, PageType type, List<SourceCitation> sources) {
        List<String> lines = type.equals(PageType.SOURCE_SUMMARY) ? documentLines(sources) : lessonLines(sources);
        if (lines.isEmpty()) {
            return body;
        }
        StringBuilder rendered = new StringBuilder(body).append("\n# Citations\n\n");
        for (int i = 0; i < lines.size(); i++) {
            rendered.append('[').append(i + 1).append("] ").append(lines.get(i)).append('\n');
        }
        return rendered.toString();
    }

    private static List<String> lessonLines(List<SourceCitation> sources) {
        Map<String, String> lines = new LinkedHashMap<>();
        sources.stream().sorted(Comparator.comparing(SourceCitation::uploadedAt)).forEach(source -> {
            if (source.conversation()) {
                lines.put(source.documentId().toString(), "Conversation, " + dateOf(source));
            } else {
                // the newest version of a lesson names it
                lines.remove(source.lessonId());
                lines.put(source.lessonId(), "[" + TextRules.escapeLinkText(TextRules.singleLine(source.lessonTitle()))
                    + "](/sources/" + source.lessonId() + ".md)");
            }
        });
        return List.copyOf(lines.values());
    }

    private static List<String> documentLines(List<SourceCitation> sources) {
        return sources.stream()
            .sorted(Comparator.comparing(SourceCitation::uploadedAt))
            .map(SourceCitation::documentId)
            .distinct()
            .map(id -> sources.stream().filter(source -> source.documentId().equals(id)).findFirst().orElseThrow())
            .map(source -> TextRules.singleLine(source.sourceFilename()) + ", uploaded " + dateOf(source))
            .toList();
    }

    private static LocalDate dateOf(SourceCitation source) {
        return LocalDate.ofInstant(source.uploadedAt(), ZoneOffset.UTC);
    }
}
