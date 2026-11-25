package com.shelflife.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body for recording units sold or thrown away from a batch. */
public record QuantityRequest(@NotNull @Positive Integer quantity) {
}
