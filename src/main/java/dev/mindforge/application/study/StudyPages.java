package dev.mindforge.application.study;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.mindforge.application.wiki.PageRenderer;
import dev.mindforge.domain.model.Identifier;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.WikiStore;

/** The live Concept pages a study scope covers, and a page's text with its superseded sections stripped. */
public final class StudyPages {

    private static final String CONVERSATION_LESSON = "conversation";

    private StudyPages() {}

    public static List<WikiPage> of(WikiStore wiki, UUID kbId, StudyScope scope) {
        return switch (scope) {
            case StudyScope.WholeKnowledgeBase whole -> wiki.listBodies(kbId, PageType.CONCEPT);
            case StudyScope.Lesson lesson when CONVERSATION_LESSON.equals(lesson.lessonId())
                || !Identifier.matches(lesson.lessonId()) -> throw new NotFoundException("Lesson " + lesson.lessonId());
            case StudyScope.Lesson lesson -> {
                Set<UUID> ids = new HashSet<>(wiki.pageIdsForLesson(kbId, lesson.lessonId()));
                yield wiki.listBodies(kbId, PageType.CONCEPT).stream().filter(page -> ids.contains(page.pageId())).toList();
            }
            case StudyScope.Page page -> {
                WikiPage root = wiki.findById(kbId, page.pageId()).orElseThrow(() -> new NotFoundException("Page"));
                Set<String> linked = wiki.outboundLinks(kbId, List.of(root.pageId())).stream()
                    .map(PageLink::targetPath).collect(Collectors.toSet());
                List<WikiPage> neighbours = wiki.findByPaths(kbId, linked);
                yield Stream.concat(Stream.of(root), neighbours.stream())
                    .filter(candidate -> candidate.type().equals(PageType.CONCEPT)).distinct().toList();
            }
        };
    }

    /** The body minus every section a live supersession marks. */
    public static String unsupersededBody(WikiStore wiki, UUID kbId, WikiPage page) {
        Set<String> superseded = wiki.liveSupersessionsOf(kbId, List.of(page.pageId())).stream()
            .map(LiveSupersession::sectionAnchor).collect(Collectors.toSet());
        return PageRenderer.withoutSections(page.markdownBody(), superseded);
    }
}
