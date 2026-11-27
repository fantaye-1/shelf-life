package com.shelflife.inventory;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Waste at one store over the last few calendar months, judged by the store's own time zone.
 *
 * @param months every month in the window, oldest first, including months with no waste
 * @param knownLossCents dollar loss of all entries in the window whose cost is known
 * @param entriesWithUnknownLoss entries with no unit cost, so the total is a lower bound when this is above zero
 * @param topItems the most wasted items in the whole window, biggest known dollar loss first
 */
public record WasteReport(
        UUID locationId,
        String locationName,
        YearMonth firstMonth,
        YearMonth lastMonth,
        List<Month> months,
        int unitsWasted,
        long knownLossCents,
        int entriesWithUnknownLoss,
        List<TopItem> topItems) {

    /** Waste in one calendar month at the store. */
    public record Month(YearMonth month, int unitsWasted, long knownLossCents, int entriesWithUnknownLoss) {
    }

    /** One item's waste over the whole window. {@code itemName} is null only if the item is gone. */
    public record TopItem(UUID itemId, String itemName, String sku, int unitsWasted, long knownLossCents,
            int entriesWithUnknownLoss) {
    }
}
