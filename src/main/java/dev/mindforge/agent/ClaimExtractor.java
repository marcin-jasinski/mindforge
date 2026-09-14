package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.Claim;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.EditItem;
import dev.mindforge.domain.model.ExtractResult;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.PlannedPage;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/**
 * Reads claims out of one chunk of a document against the index, or the changes a conversation edit asks for.
 * Titles are normalised to one line; a block range outside the chunk is dropped from its claim. One {@code VERSION}
 * covers both prompts.
 */
public class ClaimExtractor {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.LARGE;

    private static final String NONE = "(brak)";

    private final AIGateway gateway;
    private final PromptLoader prompts;
    private final int maxClaimsPerCall;

    public ClaimExtractor(AIGateway gateway, PromptLoader prompts, int maxClaimsPerCall) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.maxClaimsPerCall = maxClaimsPerCall;
    }

    public ExtractResult extract(List<ContentBlock> chunk, String renderedIndex, List<PlannedPage> plannedPages) {
        String planned = plannedPages.isEmpty() ? NONE : plannedPages.stream()
            .map(page -> "* " + page.path() + " — " + page.title())
            .collect(Collectors.joining("\n"));
        String prompt = prompts.render("claim_extractor", Map.of(
            "index", renderedIndex, "planned", planned, "blocks", render(chunk),
            "maxClaims", String.valueOf(maxClaimsPerCall)));
        DocumentOutput output = read(prompt, DocumentOutput.class);
        int first = chunk.isEmpty() ? 0 : chunk.getFirst().position();
        int last = chunk.isEmpty() ? -1 : chunk.getLast().position();
        List<Claim> claims = output.claims() == null ? List.of() : output.claims().stream()
            .filter(claim -> claim.text() != null && !claim.text().isBlank())
            .map(claim -> toClaim(claim.text(), claim.title(), claim.targetPath(),
                claim.firstBlock(), claim.lastBlock(), first, last))
            .toList();
        return new ExtractResult(claims, output.chunkDigest() == null ? "" : output.chunkDigest().trim());
    }

    public List<EditItem> extractEdit(String instruction, String quotedAnswer, String renderedIndex) {
        String prompt = prompts.render("claim_extractor_edit", Map.of(
            "index", renderedIndex, "instruction", instruction,
            "quotedAnswer", quotedAnswer == null ? NONE : quotedAnswer));
        EditOutput output = read(prompt, EditOutput.class);
        return output.items() == null ? List.of() : output.items().stream().map(this::toEditItem).toList();
    }

    private EditItem toEditItem(ItemOutput item) {
        return switch (item.kind() == null ? "" : item.kind()) {
            case "claim" -> toClaim(item.text() == null ? "" : item.text(), item.title(), item.path(), null, null, 0, -1);
            case "delete" -> new EditItem.Delete(item.path());
            case "retitle" -> new EditItem.Retitle(item.path(), item.title() == null ? "" : TextRules.singleLine(item.title()));
            default -> throw new ModelOutputException(getClass().getSimpleName(),
                new IllegalArgumentException("unknown edit item kind '" + item.kind() + "'"));
        };
    }

    private <T> T read(String prompt, Class<T> type) {
        return ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.BACKGROUND).content(), type);
    }

    private static Claim toClaim(String text, String title, String targetPath, Integer firstBlock, Integer lastBlock,
                                 int chunkFirst, int chunkLast) {
        boolean inChunk = firstBlock != null && lastBlock != null && firstBlock <= lastBlock
            && firstBlock >= chunkFirst && lastBlock <= chunkLast;
        return new Claim(text.trim(), title == null ? "" : TextRules.singleLine(title), targetPath,
            inChunk ? firstBlock : Claim.NO_BLOCKS, inChunk ? lastBlock : Claim.NO_BLOCKS);
    }

    private static String render(List<ContentBlock> chunk) {
        return chunk.stream()
            .map(block -> "[" + block.position() + "] " + (block.blockType() == BlockType.HEADING
                ? "#".repeat(((Number) block.metadata().getOrDefault(ContentBlock.LEVEL, 1)).intValue()) + " "
                : "") + block.content())
            .collect(Collectors.joining("\n\n"));
    }

    record DocumentOutput(List<ClaimOutput> claims, String chunkDigest) {}

    record ClaimOutput(String text, String title, String targetPath, Integer firstBlock, Integer lastBlock) {}

    record EditOutput(List<ItemOutput> items) {}

    record ItemOutput(String kind, String text, String title, String path) {}
}
