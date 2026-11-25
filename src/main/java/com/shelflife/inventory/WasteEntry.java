package com.shelflife.inventory;

import com.shelflife.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Units thrown away from a batch, and what they cost. Created only by {@link Batch#recordWaste}. */
@Entity
@Table(name = "waste_entry")
public class WasteEntry extends BaseEntity {

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "loss_cents", updatable = false)
    private Long lossCents;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected WasteEntry() {
    }

    WasteEntry(UUID merchantId, UUID batchId, int quantity, Long lossCents, Instant recordedAt) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId is required");
        this.batchId = Objects.requireNonNull(batchId, "batch must be saved before recording waste");
        this.quantity = quantity;
        this.lossCents = lossCents;
        this.recordedAt = recordedAt;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public int getQuantity() {
        return quantity;
    }

    /** Null when the batch had no unit cost, so the loss is unknown (not zero). */
    public Long getLossCents() {
        return lossCents;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
