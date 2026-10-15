package dev.mindforge.domain.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.PageScore;

/** Flashcards, their SM-2 schedules and the study event log. Never read by export. */
public interface StudyProgressStore {

    /** Every card of these pages, retired ones included. */
    List<CardState> cardsOf(UUID kbId, Collection<UUID> pageIds);

    /**
     * Records a regeneration of one page: new cards are inserted due now, ignoring an identical card inserted
     * concurrently; a returned card that exists takes the new anchor and hash, and if it was retired is revived with
     * its history and due now; the page's other live cards are retired.
     */
    void replaceCards(UUID kbId, UUID pageId, List<Flashcard> returned, Instant now);

    /**
     * Live cards due by {@code now} of live pages — of these pages, or all when {@code pageIds} is null — excluding
     * cards whose section has a live supersession; oldest due first.
     */
    List<CardState> dueCards(UUID kbId, Collection<UUID> pageIds, Instant now);

    Optional<CardState> findCard(UUID kbId, String cardId);

    /** Stores a card's new schedule and appends its {@code CARD} study event, together. */
    void recordReview(UUID kbId, CardState reviewed, int rating, Instant at);

    void recordQuizScore(UUID kbId, UUID pageId, int score, Instant at);

    /** Every studied page's mean over its last five events. */
    List<PageScore> pageScores(UUID kbId);
}
