package dev.mindforge.domain.model;

/**
 * One thing a source says, titled by the page it is about. {@code targetPath} is the model's proposal, verified by
 * Resolve; after Resolve it holds the final path. {@code firstBlock} and {@code lastBlock} are the positions of the
 * source blocks it came from, {@link #NO_BLOCKS} when it has none.
 */
public record Claim(String text, String title, String targetPath, int firstBlock, int lastBlock) implements EditItem {

    public static final int NO_BLOCKS = -1;

    public Claim withTargetPath(String path) {
        return new Claim(text, title, path, firstBlock, lastBlock);
    }
}
