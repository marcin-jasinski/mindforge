package dev.mindforge.discord;

import java.util.UUID;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** A slash command. It runs only after {@link DiscordCommandHandler} admitted the caller and resolved the user. */
public interface DiscordCommand {

    /** The command as registered with Discord; its name routes calls to {@link #handle}. */
    SlashCommandData definition();

    void handle(DiscordCall call, UUID userId, DiscordReply reply);
}
