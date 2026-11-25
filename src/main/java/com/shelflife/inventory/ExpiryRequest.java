package com.shelflife.inventory;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Body for fixing a mistyped expiry date. */
public record ExpiryRequest(@NotNull LocalDate expiresOn) {
}
