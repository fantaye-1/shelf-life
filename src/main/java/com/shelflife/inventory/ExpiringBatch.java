package com.shelflife.inventory;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One open batch on the expiring-soon list, with the item's name so a screen or email can show it
 * without another lookup. {@code valueAtRiskCents} is what the units still on the shelf cost the
 * store; it is null when the batch has no unit cost.
 */
public record ExpiringBatch(
        UUID batchId,
        UUID itemId,
        String itemName,
        String sku,
        LocalDate expiresOn,
        long daysUntilExpiry,
        ExpiryWindow window,
        int quantityRemaining,
        Integer unitCostCents,
        Long valueAtRiskCents,
        BatchStatus status) {

    static ExpiringBatch of(Batch batch, String itemName, String sku, LocalDate storeToday) {
        long days = batch.daysUntilExpiry(storeToday);
        Long atRisk = batch.getUnitCostCents() == null
                ? null
                : Money.times(batch.getUnitCostCents(), batch.getQuantityRemaining());
        return new ExpiringBatch(batch.getId(), batch.getItemId(), itemName, sku, batch.getExpiresOn(), days,
                ExpiryWindow.of(days), batch.getQuantityRemaining(), batch.getUnitCostCents(), atRisk,
                batch.getStatus());
    }
}
