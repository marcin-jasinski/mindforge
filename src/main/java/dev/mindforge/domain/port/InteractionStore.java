package dev.mindforge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.InteractionTurn;
import dev.mindforge.domain.model.TurnSummary;

/** Query conversations. There is no unredacted listing: nothing but Query itself reads what a turn used. */
public interface InteractionStore {

    Interaction createInteraction(UUID kbId, Interaction interaction);

    void addTurn(UUID kbId, UUID interactionId, InteractionTurn turn);

    Optional<Interaction> getInteraction(UUID kbId, UUID interactionId);

    /** The interaction's turns, oldest first. */
    List<InteractionTurn> turns(UUID kbId, UUID interactionId);

    /** The user's turns in the knowledge base, newest first, redacted to question and answer. */
    List<TurnSummary> listForUser(UUID kbId, UUID userId);
}
