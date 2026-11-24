package com.shelflife.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GtinTest {

    @ParameterizedTest
    @ValueSource(strings = {"4006381333931", "036000291452", "96385074", "10614141000415"})
    void acceptsValidBarcodesOfEveryLength(String barcode) {
        assertThat(Gtin.normalize(barcode)).isEqualTo(barcode);
        assertThat(Gtin.isValid(barcode)).isTrue();
    }

    @Test
    void stripsSpacesAndDashes() {
        assertThat(Gtin.normalize(" 0 36000-29145 2 ")).isEqualTo("036000291452");
    }

    @Test
    void blankMeansNoBarcode() {
        assertThat(Gtin.normalize(null)).isNull();
        assertThat(Gtin.normalize("  ")).isNull();
        assertThat(Gtin.isValid(null)).isFalse();
    }

    @Test
    void rejectsWrongCheckDigit() {
        assertThatThrownBy(() -> Gtin.normalize("4006381333932"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check digit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "12345678901", "123456789012345", "03600029145A"})
    void rejectsWrongLengthOrNonDigits(String barcode) {
        assertThat(Gtin.isValid(barcode)).isFalse();
    }

    @Test
    void computesGs1CheckDigit() {
        assertThat(Gtin.checkDigit("400638133393")).isEqualTo(1);
        assertThat(Gtin.checkDigit("03600029145")).isEqualTo(2);
    }
}
