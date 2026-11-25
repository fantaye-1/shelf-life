package com.shelflife.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or replacing a location. The time zone must be an IANA region id like
 * America/New_York. {@code digestEmail} is optional: it gets the morning expiry digest, and leaving
 * it out (or blank) turns the digest off.
 */
public record LocationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) String timezone,
        @Email @Size(max = 254) String digestEmail) {
}
