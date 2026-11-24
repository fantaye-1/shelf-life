package com.shelflife.inventory;

/** Money is always whole cents in a long or int; this class never uses floating point. */
public final class Money {

    private Money() {
    }

    /** unit cost x quantity, failing loudly instead of silently overflowing. */
    public static long times(int unitCents, int quantity) {
        if (unitCents < 0 || quantity < 0) {
            throw new IllegalArgumentException("Cents and quantity must not be negative");
        }
        return Math.multiplyExact((long) unitCents, (long) quantity);
    }

    /** Formats cents as dollars for logs and emails, e.g. 1234 -> "$12.34". */
    public static String format(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.absExact(cents);
        return String.format("%s$%d.%02d", sign, abs / 100, abs % 100);
    }
}
