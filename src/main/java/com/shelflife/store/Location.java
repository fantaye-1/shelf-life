package com.shelflife.store;

import com.shelflife.common.BaseEntity;
import com.shelflife.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/** One physical store. Expiry dates are judged against this location's own calendar day. */
@Entity
@Table(name = "location")
public class Location extends BaseEntity {

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "square_location_id", length = 64)
    private String squareLocationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 64)
    private String timezone;

    protected Location() {
    }

    public Location(UUID merchantId, String name, String timezone) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId is required");
        rename(name);
        changeTimezone(timezone);
    }

    public void rename(String newName) {
        this.name = Text.requireNonBlank(newName, "name");
    }

    /** Accepts IANA region ids only (e.g. America/New_York), not fixed offsets like +05:00. */
    public void changeTimezone(String zoneId) {
        String id = Text.requireNonBlank(zoneId, "timezone");
        ZoneId zone;
        try {
            zone = ZoneId.of(id);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown time zone: " + id, e);
        }
        if (!id.contains("/")) {
            throw new IllegalArgumentException("Use a region time zone like America/New_York, not " + id);
        }
        this.timezone = zone.getId();
    }

    public void linkSquare(String squareLocationId) {
        this.squareLocationId = Text.requireNonBlank(squareLocationId, "squareLocationId");
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    /** The current calendar date at this store, which may differ from the server's date. */
    public LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(zone()));
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getSquareLocationId() {
        return squareLocationId;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }
}
