package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.TokenBudget;

class TokenBudgetTest {

    @Test
    void shouldCountContextWithTheOneTokenEstimateAfterTheReserve() {
        TokenBudget budget = new TokenBudget(10, 4);

        assertThat(budget.availableForContext()).isEqualTo(6);
        assertThat(budget.fits(3, "123456789")).isTrue();
        assertThat(budget.fits(4, "123456789")).isFalse();
    }

    @Test
    void shouldRefuseAReserveLargerThanTheBudget() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TokenBudget(10, 11));
    }
}
