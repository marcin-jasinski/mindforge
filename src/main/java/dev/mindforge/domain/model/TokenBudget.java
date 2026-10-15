package dev.mindforge.domain.model;

/** How many tokens of context a prompt may carry, with room kept for the response; counted with {@link TokenEstimate}. */
public record TokenBudget(int totalTokens, int reservedForResponse) {

    public TokenBudget {
        if (reservedForResponse < 0 || reservedForResponse > totalTokens) {
            throw new IllegalArgumentException("The reserve must fit the budget");
        }
    }

    public int availableForContext() {
        return totalTokens - reservedForResponse;
    }

    /** Whether text fits after {@code used} tokens of context. */
    public boolean fits(int used, String text) {
        return used + TokenEstimate.of(text) <= availableForContext();
    }
}
