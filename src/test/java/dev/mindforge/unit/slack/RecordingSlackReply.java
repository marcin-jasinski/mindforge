package dev.mindforge.unit.slack;

import java.util.ArrayList;
import java.util.List;

import dev.mindforge.slack.SlackReply;

/** Records what a handler sent through the reply seam, in order. */
class RecordingSlackReply implements SlackReply {

    static final String ACK = "<ack>";

    final List<String> events = new ArrayList<>();

    @Override
    public void ack() {
        events.add(ACK);
    }

    @Override
    public void send(String text) {
        events.add(text);
    }
}
