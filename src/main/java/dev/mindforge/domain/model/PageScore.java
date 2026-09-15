package dev.mindforge.domain.model;

import java.util.UUID;

/** A studied page's score: the mean of its last five study events. Below {@link #WEAK_BELOW} the page is weak. */
public record PageScore(UUID pageId, double mean) {

    public static final double WEAK_BELOW = 3.0;
    public static final int EVENTS = 5;

    public boolean weak() {
        return mean < WEAK_BELOW;
    }
}
