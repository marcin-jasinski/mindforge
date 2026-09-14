package dev.mindforge.unit.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.infrastructure.event.SseProgressNotifier;

class SseProgressNotifierTest {

    private static final UUID KB = UUID.randomUUID();
    private static final RunProgress EXTRACT = new RunProgress(UUID.randomUUID(), RunKind.INGEST, UUID.randomUUID(),
        RunStatus.RUNNING, "extract", null, null, Instant.EPOCH);

    private final SseProgressNotifier notifier = new SseProgressNotifier();

    @Test
    void shouldSendStepMessagesToEverySubscriberOfTheKnowledgeBaseOnly() {
        RecordingEmitter first = new RecordingEmitter(false);
        RecordingEmitter second = new RecordingEmitter(false);
        RecordingEmitter otherKnowledgeBase = new RecordingEmitter(false);
        notifier.add(KB, first);
        notifier.add(KB, second);
        notifier.add(UUID.randomUUID(), otherKnowledgeBase);

        notifier.notify(KB, EXTRACT);

        assertThat(first.data).contains(EXTRACT);
        assertThat(second.data).contains(EXTRACT);
        assertThat(otherKnowledgeBase.attempts).isZero();
    }

    @Test
    void shouldDropAnEmitterWhoseSendFails() {
        RecordingEmitter dead = new RecordingEmitter(true);
        notifier.add(KB, dead);

        notifier.notify(KB, EXTRACT);
        notifier.notify(KB, EXTRACT);

        assertThat(dead.attempts).isEqualTo(1);
    }

    @Test
    void shouldDoNothingWithoutSubscribers() {
        assertThatNoException().isThrownBy(() -> notifier.notify(KB, EXTRACT));
    }

    private static final class RecordingEmitter extends SseEmitter {

        private final boolean failing;
        private final List<Object> data = new CopyOnWriteArrayList<>();
        private int attempts;

        private RecordingEmitter(boolean failing) {
            this.failing = failing;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            attempts++;
            if (failing) {
                throw new IOException("client gone");
            }
            builder.build().stream().map(ResponseBodyEmitter.DataWithMediaType::getData).forEach(data::add);
        }
    }
}
