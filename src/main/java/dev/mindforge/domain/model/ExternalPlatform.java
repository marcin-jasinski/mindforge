package dev.mindforge.domain.model;

/** A chat platform whose users reach MindForge through a bot. */
public enum ExternalPlatform {
    DISCORD("Discord");

    private final String label;

    ExternalPlatform(String label) {
        this.label = label;
    }

    /** The platform's display name; also the name of the knowledge base provisioned for a new bot user. */
    public String label() {
        return label;
    }
}
