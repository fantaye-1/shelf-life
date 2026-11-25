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
import java.util.regex.Pattern;

/** One physical store. Expiry dates are judged against this location's own calendar day. */
@Entity
@Table(name = "location")
public class Location extends BaseEntity {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]+$");

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "square_location_id", length = 64)
    private String squareLocationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "digest_email", length = 254)
    private String digestEmail;

    // Written only by the digest job's claim query, never through the entity.
    @Column(name = "digest_sent_on", insertable = false, updatable = false)
    private LocalDate digestSentOn;

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

    /**
     * Sets who gets this store's morning digest; null or blank turns the digest off. Only a light
     * shape check: the address is proven when mail actually arrives.
     */
    public void changeDigestEmail(String email) {
        String value = Text.blankToNull(email);
        if (value != null && (value.length() > 254 || !EMAIL.matcher(value).matches())) {
            throw new IllegalArgumentException("Not a valid email address: " + value);
        }
        this.digestEmail = value;
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

    public String getDigestEmail() {
        return digestEmail;
    }

    /** The store-local date the digest last ran for this store, or null if it never has. */
    public LocalDate getDigestSentOn() {
        return digestSentOn;
    }
}
