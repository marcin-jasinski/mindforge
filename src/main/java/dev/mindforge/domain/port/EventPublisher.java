package dev.mindforge.domain.port;

import dev.mindforge.domain.model.DomainEvent;

/**
 * Port for emitting {@link DomainEvent}s. Implementations publish within the caller's
 * active transaction so that the event and the state change it describes commit
 * together.
 */
public interface EventPublisher {

    void publish(DomainEvent event);
}
