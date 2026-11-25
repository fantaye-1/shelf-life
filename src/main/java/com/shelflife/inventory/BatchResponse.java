package com.shelflife.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A batch as the API shows it. {@code daysUntilExpiry} and {@code expired} are judged against the
 * batch's store's own calendar day: 0 means it expires today, negative means it already expired.
 */
public record BatchResponse(
        UUID id,
        UUID itemId,
        UUID locationId,
        LocalDate expiresOn,
        long daysUntilExpiry,
        boolean expired,
        int quantityReceived,
        int quantityRemaining,
        Integer unitCostCents,
        BatchStatus status,
        Instant createdAt) {

    static BatchResponse of(Batch batch, LocalDate storeToday) {
        return new BatchResponse(batch.getId(), batch.getItemId(), batch.getLocationId(),
                batch.getExpiresOn(), batch.daysUntilExpiry(storeToday), batch.isExpired(storeToday),
                batch.getQuantityReceived(), batch.getQuantityRemaining(), batch.getUnitCostCents(),
                batch.getStatus(), batch.getCreatedAt());
    }
}
