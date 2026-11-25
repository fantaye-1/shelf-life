package com.shelflife.inventory;

import java.time.Instant;
import java.util.UUID;

/** One waste record as the API shows it. {@code lossCents} is null when the batch had no unit cost. */
public record WasteResponse(
        UUID id,
        UUID batchId,
        int quantity,
        Long lossCents,
        Instant recordedAt) {

    static WasteResponse of(WasteEntry entry) {
        return new WasteResponse(entry.getId(), entry.getBatchId(), entry.getQuantity(),
                entry.getLossCents(), entry.getRecordedAt());
    }
}
