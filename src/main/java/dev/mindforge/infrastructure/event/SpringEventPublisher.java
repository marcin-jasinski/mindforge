package dev.mindforge.infrastructure.event;

import org.springframework.context.ApplicationEventPublisher;

import dev.mindforge.domain.model.DomainEvent;
import dev.mindforge.domain.port.EventPublisher;

/**
 * Publishes domain events synchronously on the caller's thread, inside its transaction; listeners that
 * act on them use {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
