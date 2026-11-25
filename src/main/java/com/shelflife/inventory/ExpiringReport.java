package com.shelflife.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The expiring-soon list for one store.
 *
 * @param storeToday the store's own calendar date the list was judged against
 * @param withinDays how many days ahead the list looks; expired batches still on the shelf are always included
 * @param counts number of batches per window, every window present (zero if empty)
 * @param knownValueAtRiskCents total cost of the listed units whose unit cost is known
 * @param batchesWithUnknownCost listed batches with no unit cost, so the total is a lower bound when this is above zero
 */
public record ExpiringReport(
        UUID locationId,
        String locationName,
        LocalDate storeToday,
        int withinDays,
        Map<ExpiryWindow, Long> counts,
        long knownValueAtRiskCents,
        int batchesWithUnknownCost,
        List<ExpiringBatch> batches) {
}
