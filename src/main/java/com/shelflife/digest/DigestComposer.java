package com.shelflife.digest;

import com.shelflife.inventory.BatchStatus;
import com.shelflife.inventory.ExpiringBatch;
import com.shelflife.inventory.ExpiringReport;
import com.shelflife.inventory.ExpiryWindow;
import com.shelflife.inventory.Money;
import com.shelflife.notify.EmailMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns one store's expiring-soon list into the morning email. Pure formatting, no I/O. */
public final class DigestComposer {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US);
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US);

    private static final Map<ExpiryWindow, String> HEADINGS = Map.of(
            ExpiryWindow.EXPIRED, "PULL NOW: expired and still on the shelf",
            ExpiryWindow.TODAY, "EXPIRES TODAY",
            ExpiryWindow.NEXT_3_DAYS, "NEXT 3 DAYS",
            ExpiryWindow.NEXT_7_DAYS, "NEXT 7 DAYS",
            ExpiryWindow.LATER, "LATER");

    private static final Map<ExpiryWindow, String> SUBJECT_LABELS = Map.of(
            ExpiryWindow.EXPIRED, "expired",
            ExpiryWindow.TODAY, "today",
            ExpiryWindow.NEXT_3_DAYS, "in 3 days",
            ExpiryWindow.NEXT_7_DAYS, "in 7 days",
            ExpiryWindow.LATER, "later");

    private DigestComposer() {
    }

    public static EmailMessage compose(String to, String merchantName, ExpiringReport report) {
        return new EmailMessage(to, subject(report), body(merchantName, report));
    }

    static String subject(ExpiringReport report) {
        List<String> parts = new ArrayList<>();
        for (ExpiryWindow window : ExpiryWindow.values()) {
            long count = report.counts().getOrDefault(window, 0L);
            if (count > 0) {
                parts.add(count + " " + SUBJECT_LABELS.get(window));
            }
        }
        String summary = parts.isEmpty() ? "nothing expiring soon" : String.join(", ", parts);
        return report.locationName() + " expiry digest: " + summary;
    }

    static String body(String merchantName, ExpiringReport report) {
        StringBuilder out = new StringBuilder();
        out.append("Good morning. Here is what expires soon at ").append(report.locationName());
        if (merchantName != null) {
            out.append(" (").append(merchantName).append(')');
        }
        out.append(", as of ").append(LONG.format(report.storeToday())).append(".\n");

        for (ExpiryWindow window : ExpiryWindow.values()) {
            List<ExpiringBatch> inWindow = report.batches().stream().filter(b -> b.window() == window).toList();
            if (inWindow.isEmpty()) {
                continue;
            }
            out.append('\n').append(HEADINGS.get(window)).append(" (").append(inWindow.size()).append(")\n");
            for (ExpiringBatch batch : inWindow) {
                out.append("- ").append(line(batch, report.storeToday())).append('\n');
            }
        }

        out.append("\nValue at risk: ").append(Money.format(report.knownValueAtRiskCents()));
        int unknown = report.batchesWithUnknownCost();
        if (unknown > 0) {
            out.append(" (").append(unknown).append(unknown == 1 ? " batch has" : " batches have")
                    .append(" no unit cost, so the real total is higher)");
        }
        out.append(".\n");
        return out.toString();
    }

    static String line(ExpiringBatch batch, LocalDate storeToday) {
        StringBuilder line = new StringBuilder(batch.itemName() == null ? "Unknown item" : batch.itemName());
        if (batch.sku() != null) {
            line.append(" [").append(batch.sku()).append(']');
        }
        line.append(": ").append(batch.quantityRemaining()).append(" left, ")
                .append(batch.expiresOn().isBefore(storeToday) ? "expired " : "expires ")
                .append(SHORT.format(batch.expiresOn())).append(", ")
                .append(batch.valueAtRiskCents() == null ? "cost unknown" : Money.format(batch.valueAtRiskCents()));
        if (batch.status() == BatchStatus.MARKED_DOWN) {
            line.append(" (already marked down)");
        }
        return line.toString();
    }
}
