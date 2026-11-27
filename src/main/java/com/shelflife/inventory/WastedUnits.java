package com.shelflife.inventory;

import java.time.Instant;
import java.util.UUID;

/** One waste record with the item it came from, as the waste report reads it. Not exposed by the API. */
public record WastedUnits(UUID itemId, int quantity, Long lossCents, Instant recordedAt) {
}
