package dev.mindforge.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mindforge.agent.AnswerWriter;
import dev.mindforge.agent.PageSelector;
import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.application.wiki.PageRenderer;
import dev.mindforge.domain.model.AnswerDraft;
import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.InteractionTurn;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.TokenBudget;
import dev.mindforge.domain.model.TokenEstimate;
import dev.mindforge.domain.model.TurnSummary;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.InteractionStore;
import dev.mindforge.domain.port.PageSearchQuery;
import dev.mindforge.domain.port.WikiStore;

/**
 * Query (ADR 0016): the rendered index and the question pick pages, code loads the live ones with their supersession
 * notes — then their linked neighbours while the budget allows — and the answer cites pages by path. Query writes no
 * page and takes no lease.
 */
public class QueryService {

    private static final int MAX_SELECTED_PAGES = 8;
    private static final int PRIOR_TURNS = 5;
    private static final TokenBudget CONTEXT = new TokenBudget(24_000, 4_000);
    private static final String NO_PRIOR_TURNS = "(brak)";
    private static final int TERMS_PER_QUESTION = 6;
    private static final int HITS_PER_TERM = 20;
    private static final int MIN_TERM_LENGTH = 4;
    /** Headroom for the frontmatter, the two headings and link-text escaping around the estimated entry lines. */
    private static final int RENDER_OVERHEAD_TOKENS = 200;

    /** An answer and the pages it cites — never the text it was grounded on. */
    public record Answer(String answer, List<String> citedPaths) {}

    private final WikiStore wiki;
    private final InteractionStore interactions;
    private final PageSelector selector;
    private final AnswerWriter writer;
    private final PageSearchQuery search;

    public QueryService(WikiStore wiki, InteractionStore interactions, PageSelector selector, AnswerWriter writer,
                        PageSearchQuery search) {
        this.wiki = wiki;
        this.interactions = interactions;
        this.selector = selector;
        this.writer = writer;
        this.search = search;
    }

    public Interaction start(UUID kbId, UUID userId) {
        return interactions.createInteraction(kbId, new Interaction(UUID.randomUUID(), kbId, userId, Instant.now()));
    }

    public Answer ask(UUID kbId, UUID userId, UUID interactionId, String question) {
        requireSession(kbId, userId, interactionId);
        List<InteractionTurn> turns = interactions.recentTurns(kbId, interactionId, PRIOR_TURNS);
        String prior = prior(turns);
        List<String> selected = selector.select(question, prior, index(kbId, question),
            MAX_SELECTED_PAGES);

        Map<String, WikiPage> live = wiki.findByPaths(kbId, selected).stream()
            .collect(Collectors.toMap(WikiPage::path, page -> page));
        List<WikiPage> chosen = selected.stream().filter(live::containsKey).map(live::get).toList();
        Set<String> chosenPaths = chosen.stream().map(WikiPage::path).collect(Collectors.toSet());
        List<String> linked = wiki.outboundLinks(kbId, chosen.stream().map(WikiPage::pageId).toList()).stream()
            .map(PageLink::targetPath).filter(path -> !chosenPaths.contains(path)).distinct().toList();
        List<WikiPage> candidates = new ArrayList<>(chosen);
        candidates.addAll(wiki.findByPaths(kbId, linked));

        Map<UUID, List<LiveSupersession>> notes = wiki.liveSupersessionsOf(kbId,
                candidates.stream().map(WikiPage::pageId).toList()).stream()
            .collect(Collectors.groupingBy(LiveSupersession::supersededPageId));
        Map<String, String> context = new LinkedHashMap<>();
        int used = 0;
        for (WikiPage page : candidates) {
            String body = PageRenderer.withNotes(page.markdownBody(), notes.getOrDefault(page.pageId(), List.of()));
            if (!CONTEXT.fits(used, body)) {
                // chosen pages outrank neighbours, so a page that does not fit ends the context
                break;
            }
            used += TokenEstimate.of(body);
            context.put(page.path(), body);
        }

        AnswerDraft draft = writer.answer(question, prior, context);
        List<String> cited = draft.citedPaths().stream().filter(context::containsKey).distinct().toList();
        interactions.addTurn(kbId, interactionId, new InteractionTurn(UUID.randomUUID(), question, draft.answer(),
            List.copyOf(context.keySet()), Instant.now()));
        return new Answer(draft.answer(), cited);
    }

    /**
     * The index a question is routed over: the whole rendered index while it fits the retrieval ceiling, past it the
     * lexical prefilter's shortlist (ADR 0016), cut so the prompt never exceeds the ceiling.
     */
    private String index(UUID kbId, String question) {
        List<IndexEntry> entries = wiki.listIndex(kbId);
        String rendered = IndexRenderer.render(entries);
        if (TokenEstimate.of(rendered) <= IndexRenderer.RETRIEVAL_CEILING_TOKENS) {
            return rendered;
        }
        Map<String, IndexEntry> shortlist = new LinkedHashMap<>();
        for (String term : terms(question)) {
            search.search(kbId, term, HITS_PER_TERM).forEach(entry -> shortlist.putIfAbsent(entry.path(), entry));
        }
        // ponytail: no term hit means the question matched nothing lexically; route over the index head, still bounded
        List<IndexEntry> candidates = shortlist.isEmpty() ? entries : List.copyOf(shortlist.values());
        List<IndexEntry> fitted = new ArrayList<>();
        int tokens = RENDER_OVERHEAD_TOKENS;
        for (IndexEntry entry : candidates) {
            tokens += TokenEstimate.of(entry.title() + entry.path() + entry.description()) + 8;
            if (tokens > IndexRenderer.RETRIEVAL_CEILING_TOKENS) {
                break;
            }
            fitted.add(entry);
        }
        return IndexRenderer.render(fitted);
    }

    /** Longest words first, each cut by two letters so a Polish inflection still matches its stem as a substring. */
    private static List<String> terms(String question) {
        return new LinkedHashSet<>(java.util.Arrays.asList(question.toLowerCase().split("[^\\p{L}\\p{N}]+"))).stream()
            .filter(word -> word.length() >= MIN_TERM_LENGTH)
            .sorted(Comparator.comparingInt(String::length).reversed())
            .limit(TERMS_PER_QUESTION)
            .map(word -> word.length() >= 6 ? word.substring(0, word.length() - 2) : word)
            .toList();
    }

    public List<TurnSummary> history(UUID kbId, UUID userId) {
        return interactions.listForUser(kbId, userId);
    }

    /** The check before anything reaches a session: it exists in the knowledge base and is the user's. */
    public void requireSession(UUID kbId, UUID userId, UUID interactionId) {
        Interaction interaction = interactions.getInteraction(kbId, interactionId)
            .orElseThrow(() -> new NotFoundException("Query session"));
        if (!interaction.userId().equals(userId)) {
            throw new NotOwnerException(kbId);
        }
    }

    private static String prior(List<InteractionTurn> turns) {
        if (turns.isEmpty()) {
            return NO_PRIOR_TURNS;
        }
        return turns.stream()
            .map(turn -> "Pytanie: " + turn.question() + "\nOdpowiedź: " + turn.answer())
            .collect(Collectors.joining("\n\n"));
    }
}
