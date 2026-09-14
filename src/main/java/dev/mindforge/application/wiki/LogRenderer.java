package dev.mindforge.application.wiki;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.mindforge.domain.model.LogEntry;
import dev.mindforge.domain.model.PagePath;
import dev.mindforge.domain.model.TextRules;

/**
 * Renders {@code log.md}: {@code # Update Log}, then {@code ## YYYY-MM-DD} groups (UTC, newest first) with one line
 * per completed run that changed something, listing only non-zero counts (T16).
 */
public final class LogRenderer {

    private LogRenderer() {}

    public static String render(List<LogEntry> entries) {
        Map<LocalDate, List<String>> days = new LinkedHashMap<>();
        entries.stream()
            .sorted(Comparator.comparing(LogEntry::finishedAt).reversed())
            .forEach(entry -> line(entry).ifPresent(line ->
                days.computeIfAbsent(dateOf(entry), day -> new ArrayList<>()).add(line)));

        StringBuilder log = new StringBuilder("# Update Log\n");
        days.forEach((day, lines) -> {
            log.append("\n## ").append(day).append('\n');
            lines.forEach(line -> log.append("* ").append(line).append('\n'));
        });
        return log.toString();
    }

    private static Optional<String> line(LogEntry entry) {
        return switch (entry.kind()) {
            case INGEST -> changes(entry).map(changes -> entry.conversation()
                ? "**Edit**: conversation — " + changes + "."
                : "**Ingest**: " + lessonLink(entry.lessonId(), entry.lessonTitle()) + " — " + changes + ".");
            case LINT -> entry.revised() == 0
                ? Optional.empty()
                : Optional.of("**Lint**: links added to " + count(entry.revised(), "page") + ".");
            case REVERT -> Optional.of("**Revert**: " + revert(entry) + ".");
        };
    }

    private static Optional<String> changes(LogEntry entry) {
        List<String> changes = new ArrayList<>();
        if (entry.created() > 0) {
            changes.add(entry.created() + " created");
        }
        if (entry.revised() > 0) {
            changes.add(entry.revised() + " revised");
        }
        if (entry.deleted() > 0) {
            changes.add(entry.deleted() + " deleted");
        }
        if (entry.supersessionCount() > 0) {
            changes.add(count(entry.supersessionCount(), "claim") + " superseded");
        }
        return changes.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", changes));
    }

    /** A revert's revisions are never revision 1: non-tombstones restored a page, tombstones removed one. */
    private static String revert(LogEntry entry) {
        String undone = describe(entry.reverted());
        int restored = entry.created() + entry.revised();
        if (restored + entry.deleted() == 0) {
            return "removed " + count(entry.supersessionCount(), "supersession") + " from " + undone;
        }
        List<String> changes = new ArrayList<>();
        if (restored > 0) {
            changes.add(count(restored, "page") + " restored");
        }
        if (entry.deleted() > 0) {
            changes.add(count(entry.deleted(), "page") + " removed");
        }
        if (entry.supersessionCount() > 0) {
            changes.add(count(entry.supersessionCount(), "supersession") + " removed");
        }
        return "undid " + undone + " — " + String.join(", ", changes);
    }

    private static String describe(LogEntry.RevertedRun run) {
        LocalDate day = LocalDate.ofInstant(run.finishedAt(), ZoneOffset.UTC);
        return switch (run.kind()) {
            case INGEST -> run.conversation()
                ? "the edit of " + day
                : "the ingest of " + day + " (" + lessonLink(run.lessonId(), run.lessonTitle()) + ")";
            case LINT -> "the Lint of " + day;
            case REVERT -> throw new IllegalStateException("A revert run cannot be reverted");
        };
    }

    private static String lessonLink(String lessonId, String lessonTitle) {
        return "[" + TextRules.escapeLinkText(lessonTitle) + "](/" + PagePath.sourceSummary(lessonId) + ".md)";
    }

    private static String count(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static LocalDate dateOf(LogEntry entry) {
        return LocalDate.ofInstant(entry.finishedAt(), ZoneOffset.UTC);
    }
}
