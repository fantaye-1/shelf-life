package com.shelflife.inventory;

import java.time.Instant;
import java.util.List;

/**
 * Waste recorded in a time window. {@code knownLossCents} adds up only entries with a known cost;
 * {@code entriesWithUnknownLoss} says how many entries had no unit cost, so the owner knows the
 * total is a floor, not the full loss.
 */
public record WasteListResponse(
        Instant from,
        Instant to,
        long knownLossCents,
        int entriesWithUnknownLoss,
        List<WasteResponse> entries) {
}
