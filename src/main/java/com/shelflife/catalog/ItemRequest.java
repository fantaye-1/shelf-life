package com.shelflife.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for creating or replacing an item. Blank sku or gtin clears it. */
public record ItemRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 64) String sku,
        // Up to 14 digits, plus room for spaces or dashes that are stripped before saving.
        @Size(max = 32) String gtin) {
}
