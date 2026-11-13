package dev.mindforge.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.infrastructure.ai.PermitGateway;
import dev.mindforge.support.StubAIGateway;

/** An image call queues on the shared background permit pool, the same as a text call. */
class PermitGatewayTest {

    private final Semaphore permits = new Semaphore(1);
    private final AtomicInteger permitsDuringCall = new AtomicInteger(-1);
    private final StubAIGateway stub = new StubAIGateway().answer("describe", prompt -> {
        permitsDuringCall.set(permits.availablePermits());
        return "a chart";
    });

    @Test
    void shouldTakeAndReleaseAPermitAroundAnImageCall() {
        PermitGateway gateway = new PermitGateway(stub, permits);

        String content = gateway.complete(ModelTier.VISION, "describe", makeImage(), DeadlineProfile.BACKGROUND)
            .content();

        assertThat(content).isEqualTo("a chart");
        assertThat(permitsDuringCall).hasValue(0);
        assertThat(permits.availablePermits()).isEqualTo(1);
        assertThat(stub.recordedCalls()).singleElement()
            .satisfies(call -> assertThat(call.image()).isEqualTo(makeImage()));
    }

    @Test
    void shouldReleaseThePermitWhenTheStubRejectsAnImageOnANonVisionTier() {
        PermitGateway gateway = new PermitGateway(stub, permits);

        assertThatThrownBy(() -> gateway.complete(ModelTier.SMALL, "describe", makeImage(),
            DeadlineProfile.BACKGROUND))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(stub.recordedCalls()).isEmpty();
        assertThat(permits.availablePermits()).isEqualTo(1);
    }

    @Test
    void shouldRecordNoImageForATextCall() {
        PermitGateway gateway = new PermitGateway(stub, permits);

        gateway.complete(ModelTier.LARGE, "describe", DeadlineProfile.BACKGROUND);

        assertThat(stub.recordedCalls()).singleElement().satisfies(call -> assertThat(call.image()).isNull());
        assertThat(permitsDuringCall).hasValue(0);
    }

    private static ImageInput makeImage() {
        return new ImageInput(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, "image/png");
    }
}
