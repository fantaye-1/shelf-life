package com.shelflife.store;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        String name,
        String timezone,
        String squareLocationId,
        Instant createdAt) {

    static LocationResponse of(Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getTimezone(),
                location.getSquareLocationId(), location.getCreatedAt());
    }
}
