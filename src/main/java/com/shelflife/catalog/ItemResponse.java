package com.shelflife.catalog;

import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        String name,
        String sku,
        String gtin,
        String squareVariationId,
        Instant createdAt) {

    static ItemResponse of(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getSku(), item.getGtin(),
                item.getSquareVariationId(), item.getCreatedAt());
    }
}
