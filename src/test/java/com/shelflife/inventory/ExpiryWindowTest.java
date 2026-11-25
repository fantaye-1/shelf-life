package com.shelflife.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpiryWindowTest {

    @ParameterizedTest
    @CsvSource({
            "-30, EXPIRED",
            "-1, EXPIRED",
            "0, TODAY",
            "1, NEXT_3_DAYS",
            "3, NEXT_3_DAYS",
            "4, NEXT_7_DAYS",
            "7, NEXT_7_DAYS",
            "8, LATER",
            "30, LATER"})
    void bucketsByDaysLeft(long daysUntilExpiry, ExpiryWindow expected) {
        assertThat(ExpiryWindow.of(daysUntilExpiry)).isEqualTo(expected);
    }
}
