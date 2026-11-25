package com.shelflife.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for logging a new batch. {@code expiresOn} is the date printed on the package (YYYY-MM-DD),
 * read as a day in the store's own time zone. {@code unitCostCents} is optional; without it the
 * dollar loss of wasted units is unknown.
 */
public record BatchRequest(
        @NotNull UUID itemId,
        @NotNull UUID locationId,
        @NotNull LocalDate expiresOn,
        @NotNull @Positive Integer quantity,
        @PositiveOrZero Integer unitCostCents) {
}
