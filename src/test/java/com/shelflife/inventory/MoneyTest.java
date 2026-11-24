package com.shelflife.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void multipliesCentsExactly() {
        assertThat(Money.times(199, 3)).isEqualTo(597);
        assertThat(Money.times(0, 10)).isZero();
    }

    @Test
    void largeTotalsDoNotOverflowInt() {
        assertThat(Money.times(Integer.MAX_VALUE, 2)).isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test
    void rejectsNegativeInputs() {
        assertThatThrownBy(() -> Money.times(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.times(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsAsDollars() {
        assertThat(Money.format(1234)).isEqualTo("$12.34");
        assertThat(Money.format(5)).isEqualTo("$0.05");
        assertThat(Money.format(0)).isEqualTo("$0.00");
        assertThat(Money.format(-250)).isEqualTo("-$2.50");
    }
}
