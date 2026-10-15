package dev.mindforge.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.PageScore;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.infrastructure.persistence.entity.FlashcardEntity;
import dev.mindforge.infrastructure.persistence.entity.StudyEventEntity;
import dev.mindforge.infrastructure.persistence.jpa.FlashcardJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.StudyEventJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.StudyEntityMapper;

@Transactional
public class StudyProgressStoreAdapter implements StudyProgressStore {

    private final FlashcardJpaRepository cards;
    private final StudyEventJpaRepository events;
    private final StudyEntityMapper mapper;

    public StudyProgressStoreAdapter(FlashcardJpaRepository cards, StudyEventJpaRepository events,
                                     StudyEntityMapper mapper) {
        this.cards = cards;
        this.events = events;
        this.mapper = mapper;
    }

    @Override
    public List<CardState> cardsOf(UUID kbId, Collection<UUID> pageIds) {
        return cards.findByKnowledgeBaseIdAndPageIdIn(kbId, pageIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void replaceCards(UUID kbId, UUID pageId, List<Flashcard> returned, Instant now) {
        for (Flashcard card : returned) {
            if (cards.insertIfAbsent(kbId, card.cardId(), pageId, card.sectionAnchor(), card.cardType().name(),
                card.front(), card.back(), card.sourceHash(), now) == 0) {
                cards.refresh(kbId, card.cardId(), card.sectionAnchor(), card.sourceHash(), now);
            }
        }
        if (returned.isEmpty()) {
            cards.retireAll(kbId, pageId, now);
        } else {
            cards.retireOthers(kbId, pageId, returned.stream().map(Flashcard::cardId).toList(), now);
        }
    }

    @Override
    public List<CardState> dueCards(UUID kbId, Collection<UUID> pageIds, Instant now) {
        if (pageIds != null && pageIds.isEmpty()) {
            return List.of();
        }
        List<FlashcardEntity> due = pageIds == null ? cards.findDue(kbId, now) : cards.findDueOfPages(kbId, pageIds, now);
        return due.stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<CardState> findCard(UUID kbId, String cardId) {
        return cards.findById(new FlashcardEntity.Key(kbId, cardId)).map(mapper::toDomain);
    }

    @Override
    public void recordReview(UUID kbId, CardState reviewed, int rating, Instant at) {
        FlashcardEntity card = cards.findById(new FlashcardEntity.Key(kbId, reviewed.card().cardId())).orElseThrow();
        card.setEaseFactor(reviewed.easeFactor());
        card.setIntervalDays(reviewed.intervalDays());
        card.setRepetitions(reviewed.repetitions());
        card.setDueAt(reviewed.dueAt());
        events.save(StudyEventEntity.of(kbId, card.getPageId(), card.getCardId(), StudyEventEntity.CARD, rating, at));
    }

    @Override
    public void recordQuizScore(UUID kbId, UUID pageId, int score, Instant at) {
        events.save(StudyEventEntity.of(kbId, pageId, null, StudyEventEntity.QUIZ, score, at));
    }

    @Override
    public List<PageScore> pageScores(UUID kbId) {
        return events.findPageScores(kbId).stream().map(row -> new PageScore(row.getPageId(), row.getMean())).toList();
    }
}
