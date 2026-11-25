package com.shelflife.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for creating or replacing a location. The time zone must be an IANA region id like America/New_York. */
public record LocationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) String timezone) {
}
