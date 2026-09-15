package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.CardDraft;
import dev.mindforge.domain.model.CardType;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/**
 * Cuts flashcards from one Concept page, reusing still-correct cards verbatim (ADR 0018). A card of an unknown type or
 * without a front and back is dropped; an anchor that is not one of the page's sections becomes null.
 */
public class FlashcardGenerator {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.LARGE;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public FlashcardGenerator(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    /**
     * @param body       the page body with superseded sections stripped
     * @param anchors    the level-1 anchors of that body
     * @param current    the page's live cards
     * @param reusable   retired cards cut from exactly this content, offered for verbatim revival
     */
    public List<CardDraft> generate(String title, String body, Set<String> anchors, List<Flashcard> current,
                                    List<Flashcard> reusable) {
        String prompt = prompts.render("flashcard_generator", Map.of(
            "title", title, "body", body, "sections", anchors.isEmpty() ? "(brak)" : String.join(", ", anchors),
            "current", render(current), "reusable", render(reusable)));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.BATCH).content(), Output.class);
        return output.cards() == null ? List.of() : output.cards().stream()
            .filter(card -> card.front() != null && !card.front().isBlank() && card.back() != null
                && !card.back().isBlank() && isType(card.type()))
            .map(card -> new CardDraft(CardType.valueOf(card.type()), card.front().strip(), card.back().strip(),
                anchors.contains(card.section()) ? card.section() : null))
            .toList();
    }

    private static boolean isType(String type) {
        return type != null && Set.of("BASIC", "CLOZE", "REVERSE").contains(type);
    }

    private static String render(List<Flashcard> cards) {
        return cards.isEmpty() ? "(brak)" : cards.stream()
            .map(FlashcardGenerator::line)
            .collect(Collectors.joining("\n"));
    }

    /** One existing card as the prompt shows it, with every field the answer must copy to reuse it. */
    private static String line(Flashcard card) {
        return "* type: " + card.cardType() + " | section: " + card.sectionAnchor() + " | front: " + card.front()
            + " | back: " + card.back();
    }

    record Output(List<CardOutput> cards) {}

    record CardOutput(String type, String front, String back, String section) {}

}
