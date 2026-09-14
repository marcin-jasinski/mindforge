package dev.mindforge.application.ingest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.mindforge.domain.model.CandidateSection;
import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.SupersessionProposal;
import dev.mindforge.domain.model.TokenEstimate;
import dev.mindforge.domain.model.WikiPage;

/** What Supersede is shown, and which of its proposals survive (T14). */
public final class SupersessionInputs {

    /** The sections shown, and how many candidate pages did not fit the budget. */
    public record Selection(List<CandidateSection> sections, int omittedPages) {}

    private SupersessionInputs() {}

    /**
     * Ranks candidate pages by the links joining them to the run's pages, then by path, and adds whole pages' level-1
     * sections — minus those already superseded — until the next page would not fit the budget.
     *
     * @param linkCounts  connecting links per candidate path
     * @param superseded  {@code path#anchor} of every section that already has a live supersession
     */
    public static Selection select(List<WikiPage> candidates, Map<String, Integer> linkCounts,
                                   Set<String> superseded, int budgetTokens) {
        List<WikiPage> ranked = candidates.stream()
            .sorted(Comparator.<WikiPage>comparingInt(page -> linkCounts.getOrDefault(page.path(), 0)).reversed()
                .thenComparing(WikiPage::path))
            .toList();
        List<CandidateSection> shown = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < ranked.size(); i++) {
            List<CandidateSection> sections = sections(ranked.get(i), superseded);
            int tokens = sections.stream().mapToInt(section -> TokenEstimate.of(section.text())).sum();
            if (used + tokens > budgetTokens) {
                return new Selection(shown, ranked.size() - i);
            }
            shown.addAll(sections);
            used += tokens;
        }
        return new Selection(shown, 0);
    }

    /**
     * Keeps a proposal only if its section was shown, its superseding path is a Concept this run revised, the two
     * paths differ, and no earlier proposal names the same section.
     */
    public static List<SupersessionProposal> verify(List<SupersessionProposal> proposals,
                                                    List<CandidateSection> shown, Set<String> revisedConcepts) {
        Set<String> shownKeys = new HashSet<>();
        shown.forEach(section -> shownKeys.add(key(section.path(), section.anchor())));
        Set<String> kept = new HashSet<>();
        return proposals.stream()
            .filter(proposal -> shownKeys.contains(key(proposal.supersededPath(), proposal.sectionAnchor()))
                && revisedConcepts.contains(proposal.supersedingPath())
                && !proposal.supersededPath().equals(proposal.supersedingPath())
                && kept.add(key(proposal.supersededPath(), proposal.sectionAnchor())))
            .toList();
    }

    public static String key(String path, String anchor) {
        return path + "#" + anchor;
    }

    private static List<CandidateSection> sections(WikiPage page, Set<String> superseded) {
        String body = page.markdownBody();
        Set<String> seen = new HashSet<>();
        return MarkdownStructure.sections(body).stream()
            .filter(section -> seen.add(section.anchor()) && !superseded.contains(key(page.path(), section.anchor())))
            .map(section -> {
                int headingEnd = body.indexOf('\n', section.start());
                String text = headingEnd < 0 || headingEnd >= section.end()
                    ? ""
                    : body.substring(headingEnd + 1, section.end()).strip();
                return new CandidateSection(page.path(), section.anchor(), section.heading(), text);
            })
            .toList();
    }
}
