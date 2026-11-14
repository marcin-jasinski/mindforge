package dev.mindforge.unit.discord;

import java.util.ArrayList;
import java.util.List;

import dev.mindforge.discord.DiscordReply;

/** Records what a handler sent through the reply seam, in order. */
class RecordingDiscordReply implements DiscordReply {

    static final String DEFER = "<defer>";

    final List<String> events = new ArrayList<>();

    @Override
    public void defer() {
        events.add(DEFER);
    }

    @Override
    public void send(String text) {
        events.add(text);
    }
}
