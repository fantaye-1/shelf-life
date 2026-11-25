package com.shelflife.inventory;

import com.shelflife.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One delivery of one item at one location, sharing one expiry date.
 *
 * <p>Status rules:
 * <ul>
 *   <li>A new batch is {@link BatchStatus#ACTIVE} with everything still on the shelf.</li>
 *   <li>An open batch (active or marked down) can be marked down, sold from or wasted from.</li>
 *   <li>When the last unit leaves, the batch closes: {@link BatchStatus#SOLD_OUT} if it was sold,
 *       {@link BatchStatus#WASTED} if it was thrown away. Closed batches can't change.</li>
 * </ul>
 */
@Entity
@Table(name = "batch")
public class Batch extends BaseEntity {

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "quantity_remaining", nullable = false)
    private int quantityRemaining;

    @Column(name = "unit_cost_cents")
    private Integer unitCostCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchStatus status;

    // Two phones editing the same batch at once must not both succeed.
    @Version
    @Column(nullable = false)
    private long version;

    protected Batch() {
    }

    /**
     * @param unitCostCents what the store paid per unit, in cents; null if unknown
     */
    public Batch(UUID merchantId, UUID itemId, UUID locationId, LocalDate expiresOn, int quantity,
            Integer unitCostCents) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId is required");
        this.itemId = Objects.requireNonNull(itemId, "itemId is required");
        this.locationId = Objects.requireNonNull(locationId, "locationId is required");
        this.expiresOn = Objects.requireNonNull(expiresOn, "expiresOn is required");
        requirePositive(quantity, "quantity");
        if (unitCostCents != null && unitCostCents < 0) {
            throw new IllegalArgumentException("unitCostCents must not be negative");
        }
        this.quantityReceived = quantity;
        this.quantityRemaining = quantity;
        this.unitCostCents = unitCostCents;
        this.status = BatchStatus.ACTIVE;
    }

    /** Fixes a mistyped expiry date while the batch is still on the shelf. */
    public void correctExpiry(LocalDate newExpiresOn) {
        requireOpen();
        this.expiresOn = Objects.requireNonNull(newExpiresOn, "expiresOn is required");
    }

    /** Marks the batch down for a quick sale. Marking down twice is harmless. */
    public void markDown() {
        requireOpen();
        this.status = BatchStatus.MARKED_DOWN;
    }

    /** Records units sold. Selling the last unit closes the batch as sold out. */
    public void recordSale(int quantity) {
        take(quantity);
        if (quantityRemaining == 0) {
            status = BatchStatus.SOLD_OUT;
        }
    }

    /** Staff found the shelf empty: whatever was left is treated as sold. */
    public void markSoldOut() {
        requireOpen();
        recordSale(quantityRemaining);
    }

    /**
     * Records units thrown away and returns the waste entry to save. Throwing away the last unit
     * closes the batch as wasted.
     */
    public WasteEntry recordWaste(int quantity, Instant recordedAt) {
        Objects.requireNonNull(recordedAt, "recordedAt is required");
        Objects.requireNonNull(getId(), "batch must be saved before recording waste");
        take(quantity);
        if (quantityRemaining == 0) {
            status = BatchStatus.WASTED;
        }
        Long lossCents = unitCostCents == null ? null : Money.times(unitCostCents, quantity);
        return new WasteEntry(merchantId, getId(), quantity, lossCents, recordedAt);
    }

    /** Days from the store's today until expiry: 0 = expires today, negative = already expired. */
    public long daysUntilExpiry(LocalDate storeToday) {
        return ChronoUnit.DAYS.between(storeToday, expiresOn);
    }

    public boolean isExpired(LocalDate storeToday) {
        return expiresOn.isBefore(storeToday);
    }

    private void take(int quantity) {
        requireOpen();
        requirePositive(quantity, "quantity");
        if (quantity > quantityRemaining) {
            throw new IllegalArgumentException(
                    "Only " + quantityRemaining + " left in this batch, can't remove " + quantity);
        }
        quantityRemaining -= quantity;
    }

    private void requireOpen() {
        if (!status.isOpen()) {
            throw new IllegalStateException("Batch is " + status + " and can no longer change");
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public int getQuantityRemaining() {
        return quantityRemaining;
    }

    public Integer getUnitCostCents() {
        return unitCostCents;
    }

    public BatchStatus getStatus() {
        return status;
    }
}
