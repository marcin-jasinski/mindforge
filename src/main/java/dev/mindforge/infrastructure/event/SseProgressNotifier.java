package dev.mindforge.infrastructure.event;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.port.ProgressNotifier;

/**
 * Streams run progress over SSE, one set of emitters per knowledge base, so several tabs work. An emitter that
 * completes, times out or fails a send is dropped. In memory: the single-instance ceiling applies.
 */
public class SseProgressNotifier implements ProgressNotifier {

    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID kbId) {
        return add(kbId, new SseEmitter(TIMEOUT_MILLIS));
    }

    /** Registers an emitter for a knowledge base's progress. */
    public SseEmitter add(UUID kbId, SseEmitter emitter) {
        emitters.computeIfAbsent(kbId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(kbId, emitter));
        emitter.onTimeout(() -> remove(kbId, emitter));
        emitter.onError(error -> remove(kbId, emitter));
        return emitter;
    }

    @Override
    public void notify(UUID kbId, RunProgress progress) {
        for (SseEmitter emitter : emitters.getOrDefault(kbId, Set.of())) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(progress));
            } catch (Exception e) {
                remove(kbId, emitter);
            }
        }
    }

    private void remove(UUID kbId, SseEmitter emitter) {
        emitters.computeIfPresent(kbId, (key, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
