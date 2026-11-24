package com.shelflife.common;

import java.util.Objects;

/** Input checks shared by the domain entities. */
public final class Text {

    private Text() {
    }

    /** Returns the value stripped of surrounding whitespace; rejects null and blank values. */
    public static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return stripped;
    }

    /** Returns null for null or blank values, otherwise the stripped value. */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
