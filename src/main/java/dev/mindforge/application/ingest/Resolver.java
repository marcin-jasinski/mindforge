package dev.mindforge.application.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.Claim;
import dev.mindforge.domain.model.EditItem;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.IngestRunFailedException;
import dev.mindforge.domain.model.PagePath;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWriteTask;
import dev.mindforge.domain.model.PlannedPage;
import dev.mindforge.domain.model.TextRules;

/**
 * Resolve (T22, T15, T28): turns a run's claims and edit items into exactly one task per path. Code only.
 *
 * <ol>
 *   <li>A claim's target counts only if it is a live Concept or a path an earlier chunk planned; otherwise its path
 *       is derived from its title — a revision if that path is live, else a create.
 *   <li>Claims are grouped by final path; a create takes the title of its first claim, a revision keeps the page's.
 *   <li>The page-task cap, then deletions and retitles of live Concepts, then the Source Summary task.
 * </ol>
 * Everything dropped is recorded as a failure. A run whose source is an article may only create pages.
 */
public final class Resolver {

    public static final int MAX_TITLE_LENGTH = 200;

    /** What a run writes: tasks, deletions and retitles without a task, by path; and what Resolve dropped. */
    public record Plan(List<PageWriteTask> tasks, Set<String> deletions, Map<String, String> retitles,
                       List<Map<String, Object>> failures) {}

    private final Map<String, IndexEntry> live;
    private final boolean createOnly;
    private final Map<String, List<Claim>> claimsByPath = new LinkedHashMap<>();
    private final List<Map<String, Object>> failures = new ArrayList<>();

    public Resolver(List<IndexEntry> index, boolean createOnly) {
        this.live = index.stream().collect(Collectors.toMap(IndexEntry::path, Function.identity()));
        this.createOnly = createOnly;
    }

    /** Resolves one chunk's claims, in document order, against live Concepts and the paths earlier chunks planned. */
    public void addClaims(List<Claim> claims) {
        Set<String> plannedEarlier = Set.copyOf(claimsByPath.keySet());
        for (Claim claim : claims) {
            String target = claim.targetPath();
            String path = target != null && (isLiveConcept(target) || plannedEarlier.contains(target))
                ? target
                : PagePath.concept(claim.title());
            if (createOnly && live.containsKey(path)) {
                failures.add(dropped("claim", path, "an article may only create pages"));
                continue;
            }
            claimsByPath.computeIfAbsent(path, key -> new ArrayList<>()).add(claim.withTargetPath(path));
        }
    }

    /** The paths and titles planned so far, for the next chunk's Extract call. */
    public List<PlannedPage> planned() {
        return claimsByPath.entrySet().stream()
            .map(entry -> new PlannedPage(entry.getKey(), titleOf(entry.getKey(), entry.getValue())))
            .toList();
    }

    /**
     * @param edits           the run's deletions and retitles; claims among them are ignored here
     * @param sourceSummary   the Source Summary's path, or null for a conversation turn
     * @param lessonTitle     the Source Summary's title
     * @throws IngestRunFailedException over the page-task cap, before any write
     */
    public Plan finish(List<EditItem> edits, String sourceSummary, String lessonTitle, int maxPageTasks) {
        if (claimsByPath.size() > maxPageTasks) {
            throw new IngestRunFailedException("would write " + claimsByPath.size() + " pages (limit " + maxPageTasks
                + ") — split the document", false);
        }
        Map<String, PageWriteTask> tasks = new LinkedHashMap<>();
        claimsByPath.forEach((path, claims) -> {
            String title = titleOf(path, claims);
            if (isValidTitle(title)) {
                tasks.put(path, new PageWriteTask(path, PageType.CONCEPT, title, !live.containsKey(path), claims));
            } else {
                failures.add(dropped("claim", path, "invalid title"));
            }
        });

        Set<String> deletions = new LinkedHashSet<>();
        Map<String, String> retitles = new LinkedHashMap<>();
        for (EditItem edit : edits) {
            switch (edit) {
                case EditItem.Delete delete when !isLiveConcept(delete.path()) ->
                    failures.add(dropped("delete", delete.path(), "not a live Concept"));
                case EditItem.Delete delete -> deletions.add(delete.path());
                case EditItem.Retitle retitle when !isLiveConcept(retitle.path()) ->
                    failures.add(dropped("retitle", retitle.path(), "not a live Concept"));
                case EditItem.Retitle retitle when !isValidTitle(retitle.title()) ->
                    failures.add(dropped("retitle", retitle.path(), "invalid title"));
                case EditItem.Retitle retitle when retitles.containsKey(retitle.path()) ->
                    failures.add(dropped("retitle", retitle.path(), "retitled twice"));
                case EditItem.Retitle retitle -> retitles.put(retitle.path(), retitle.title());
                case Claim claim -> { }
            }
        }
        for (String path : List.copyOf(deletions)) {
            if (tasks.containsKey(path) || retitles.containsKey(path)) {
                failures.add(dropped("delete", path, "the same edit also writes this page"));
                if (tasks.remove(path) != null) {
                    failures.add(dropped("claim", path, "the same edit also deletes this page"));
                }
                if (retitles.remove(path) != null) {
                    failures.add(dropped("retitle", path, "the same edit also deletes this page"));
                }
                deletions.remove(path);
            }
        }
        retitles.entrySet().removeIf(retitle -> {
            PageWriteTask task = tasks.get(retitle.getKey());
            if (task != null) {
                tasks.put(task.path(), new PageWriteTask(task.path(), task.type(), retitle.getValue(), false,
                    task.claims()));
            }
            return task != null;
        });

        if (sourceSummary != null) {
            tasks.put(sourceSummary, new PageWriteTask(sourceSummary, PageType.SOURCE_SUMMARY, lessonTitle,
                !live.containsKey(sourceSummary), List.of()));
        }
        return new Plan(List.copyOf(tasks.values()), deletions, retitles, List.copyOf(failures));
    }

    public static boolean isValidTitle(String title) {
        String normalised = TextRules.singleLine(title);
        int length = normalised.codePointCount(0, normalised.length());
        return normalised.equals(title) && length >= 1 && length <= MAX_TITLE_LENGTH;
    }

    private String titleOf(String path, List<Claim> claims) {
        IndexEntry page = live.get(path);
        return page != null ? page.title() : claims.getFirst().title();
    }

    private boolean isLiveConcept(String path) {
        IndexEntry page = path == null ? null : live.get(path);
        return page != null && page.type().equals(PageType.CONCEPT);
    }

    private static Map<String, Object> dropped(String item, String path, String reason) {
        return Map.of("step", "resolve", "item", item, "path", String.valueOf(path), "reason", reason);
    }
}
