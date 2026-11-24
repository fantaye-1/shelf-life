package com.shelflife.catalog;

/**
 * Barcode numbers (GTIN-8, UPC-A/GTIN-12, EAN-13, GTIN-14) as printed on products.
 * Checking the check digit catches most mistyped or misscanned barcodes before they are saved.
 */
public final class Gtin {

    private Gtin() {
    }

    /**
     * Returns the barcode with spaces and dashes removed, or null when the input is null or blank.
     *
     * @throws IllegalArgumentException if it is not 8, 12, 13 or 14 digits with a valid check digit
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replace(" ", "").replace("-", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (!digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("A barcode may contain only digits: " + raw);
        }
        int length = digits.length();
        if (length != 8 && length != 12 && length != 13 && length != 14) {
            throw new IllegalArgumentException("A barcode must have 8, 12, 13 or 14 digits: " + raw);
        }
        if (checkDigit(digits.substring(0, length - 1)) != digits.charAt(length - 1) - '0') {
            throw new IllegalArgumentException("Barcode check digit is wrong: " + raw);
        }
        return digits;
    }

    public static boolean isValid(String raw) {
        try {
            return normalize(raw) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** GS1 mod-10: weights 3,1,3,1,... starting from the rightmost digit of the payload. */
    static int checkDigit(String payload) {
        int sum = 0;
        for (int i = 0; i < payload.length(); i++) {
            int digit = payload.charAt(payload.length() - 1 - i) - '0';
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }
        return (10 - sum % 10) % 10;
    }
}
