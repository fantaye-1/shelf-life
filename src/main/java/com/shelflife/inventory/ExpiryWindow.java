package com.shelflife.inventory;

/**
 * How urgent a batch is, judged by days until expiry at its own store. The buckets match what the
 * owner asked for: already expired, today, within 3 days, within 7 days.
 */
public enum ExpiryWindow {
    /** Past its date and still on the shelf: pull it now. */
    EXPIRED,
    TODAY,
    /** 1 to 3 days left. */
    NEXT_3_DAYS,
    /** 4 to 7 days left. */
    NEXT_7_DAYS,
    /** More than 7 days left; only shown when a longer look-ahead is asked for. */
    LATER;

    public static ExpiryWindow of(long daysUntilExpiry) {
        if (daysUntilExpiry < 0) {
            return EXPIRED;
        }
        if (daysUntilExpiry == 0) {
            return TODAY;
        }
        if (daysUntilExpiry <= 3) {
            return NEXT_3_DAYS;
        }
        if (daysUntilExpiry <= 7) {
            return NEXT_7_DAYS;
        }
        return LATER;
    }
}
