package com.shelflife.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocationTest {

    private final UUID merchantId = UUID.randomUUID();

    @Test
    void todayUsesTheStoresTimeZoneNotTheServers() {
        // 03:30 UTC on Nov 25 is still 22:30 on Nov 24 in Dayton, OH.
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T03:30:00Z"), ZoneOffset.UTC);
        Location dayton = new Location(merchantId, "Zmarket23", "America/New_York");

        assertThat(dayton.today(clock)).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void rejectsUnknownAndOffsetTimeZones() {
        assertThatThrownBy(() -> new Location(merchantId, "Shop", "Mars/Olympus"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Location(merchantId, "Shop", "-05:00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trimsAndRequiresName() {
        assertThat(new Location(merchantId, "  Main St  ", "America/Chicago").getName()).isEqualTo("Main St");
        assertThatThrownBy(() -> new Location(merchantId, " ", "America/Chicago"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
