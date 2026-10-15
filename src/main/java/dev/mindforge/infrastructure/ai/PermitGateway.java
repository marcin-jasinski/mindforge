package dev.mindforge.infrastructure.ai;

import java.util.concurrent.Semaphore;

import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;

/**
 * The gateway background runs call through: every call first takes a permit from the one pool shared by every
 * {@code INGEST} and {@code LINT} run, so a burst of runs across knowledge bases queues instead of opening the
 * shared circuit breaker. Query and study calls do not use it.
 */
public class PermitGateway implements AIGateway {

    private final AIGateway delegate;
    private final Semaphore permits;

    public PermitGateway(AIGateway delegate, Semaphore permits) {
        this.delegate = delegate;
        this.permits = permits;
    }

    @Override
    public CompletionResult complete(ModelTier tier, String prompt, DeadlineProfile deadline) {
        permits.acquireUninterruptibly();
        try {
            return delegate.complete(tier, prompt, deadline);
        } finally {
            permits.release();
        }
    }
}
