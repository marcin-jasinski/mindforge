package dev.mindforge.domain.model;

/** A recall rating on SM-2's 0–5 scale; quiz grades use the same scale (T27). */
public record ReviewResult(int rating) {

    public static final int MIN = 0;
    public static final int MAX = 5;

    public ReviewResult {
        if (rating < MIN || rating > MAX) {
            throw new IllegalArgumentException("A rating is 0-5, not " + rating);
        }
    }

    /** A model's grade, clamped onto the scale. */
    public static ReviewResult clamped(int score) {
        return new ReviewResult(Math.max(MIN, Math.min(MAX, score)));
    }
}
