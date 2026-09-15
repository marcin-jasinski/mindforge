package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.InteractionTurn;
import dev.mindforge.domain.model.TurnSummary;
import dev.mindforge.domain.port.InteractionStore;
import dev.mindforge.infrastructure.persistence.jpa.InteractionJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.InteractionTurnJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.InteractionEntityMapper;

@Transactional
public class InteractionStoreAdapter implements InteractionStore {

    private final InteractionJpaRepository interactions;
    private final InteractionTurnJpaRepository turns;
    private final InteractionEntityMapper mapper;

    public InteractionStoreAdapter(InteractionJpaRepository interactions, InteractionTurnJpaRepository turns,
                                   InteractionEntityMapper mapper) {
        this.interactions = interactions;
        this.turns = turns;
        this.mapper = mapper;
    }

    @Override
    public Interaction createInteraction(UUID kbId, Interaction interaction) {
        if (!kbId.equals(interaction.knowledgeBaseId())) {
            throw new IllegalArgumentException("Interaction does not belong to knowledge base " + kbId);
        }
        return mapper.toDomain(interactions.saveAndFlush(mapper.toEntity(interaction)));
    }

    @Override
    public void addTurn(UUID kbId, UUID interactionId, InteractionTurn turn) {
        turns.save(mapper.toEntity(turn, kbId, interactionId));
    }

    @Override
    public Optional<Interaction> getInteraction(UUID kbId, UUID interactionId) {
        return interactions.findByKnowledgeBaseIdAndInteractionId(kbId, interactionId).map(mapper::toDomain);
    }

    @Override
    public List<InteractionTurn> turns(UUID kbId, UUID interactionId) {
        return turns.findByKnowledgeBaseIdAndInteractionIdOrderByCreatedAt(kbId, interactionId).stream()
            .map(mapper::toDomain).toList();
    }

    @Override
    public List<TurnSummary> listForUser(UUID kbId, UUID userId) {
        return turns.findSummaries(kbId, userId);
    }
}
