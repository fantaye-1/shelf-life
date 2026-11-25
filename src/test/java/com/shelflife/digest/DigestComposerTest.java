package com.shelflife.digest;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelflife.inventory.BatchStatus;
import com.shelflife.inventory.ExpiringBatch;
import com.shelflife.inventory.ExpiringReport;
import com.shelflife.inventory.ExpiryWindow;
import com.shelflife.notify.EmailMessage;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DigestComposerTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-24"); // a Tuesday

    private static ExpiringBatch batch(String name, String sku, String expiresOn, int qty, Integer unitCents,
            BatchStatus status) {
        LocalDate date = LocalDate.parse(expiresOn);
        long days = TODAY.until(date, java.time.temporal.ChronoUnit.DAYS);
        Long atRisk = unitCents == null ? null : (long) unitCents * qty;
        return new ExpiringBatch(UUID.randomUUID(), UUID.randomUUID(), name, sku, date, days, ExpiryWindow.of(days),
                qty, unitCents, atRisk, status);
    }

    private static ExpiringReport report(List<ExpiringBatch> batches) {
        Map<ExpiryWindow, Long> counts = new EnumMap<>(ExpiryWindow.class);
        for (ExpiryWindow window : ExpiryWindow.values()) {
            counts.put(window, batches.stream().filter(b -> b.window() == window).count());
        }
        long known = batches.stream().filter(b -> b.valueAtRiskCents() != null)
                .mapToLong(ExpiringBatch::valueAtRiskCents).sum();
        int unknown = (int) batches.stream().filter(b -> b.valueAtRiskCents() == null).count();
        return new ExpiringReport(UUID.randomUUID(), "Dayton", TODAY, 7, counts, known, unknown, batches);
    }

    @Test
    void groupsBatchesByUrgencyWithDollarsAtRisk() {
        ExpiringReport report = report(List.of(
                batch("Whole milk 1 gal", "MILK-1G", "2026-11-22", 2, 250, BatchStatus.ACTIVE),
                batch("Greek yogurt", null, "2026-11-24", 4, 150, BatchStatus.ACTIVE),
                batch("Injera pack", null, "2026-11-27", 10, null, BatchStatus.ACTIVE),
                batch("Whole milk 1 gal", "MILK-1G", "2026-11-30", 3, 300, BatchStatus.MARKED_DOWN)));

        EmailMessage email = DigestComposer.compose("owner@zmarket23.example", "Zmarket23", report);

        assertThat(email.to()).isEqualTo("owner@zmarket23.example");
        assertThat(email.subject())
                .isEqualTo("Dayton expiry digest: 1 expired, 1 today, 1 in 3 days, 1 in 7 days");
        assertThat(email.body())
                .startsWith("Good morning. Here is what expires soon at Dayton (Zmarket23), as of Tuesday, Nov 24, 2026.")
                .contains("PULL NOW: expired and still on the shelf (1)\n"
                        + "- Whole milk 1 gal [MILK-1G]: 2 left, expired Sun Nov 22, $5.00\n")
                .contains("EXPIRES TODAY (1)\n- Greek yogurt: 4 left, expires Tue Nov 24, $6.00\n")
                .contains("NEXT 3 DAYS (1)\n- Injera pack: 10 left, expires Fri Nov 27, cost unknown\n")
                .contains("NEXT 7 DAYS (1)\n"
                        + "- Whole milk 1 gal [MILK-1G]: 3 left, expires Mon Nov 30, $9.00 (already marked down)\n")
                .endsWith("Value at risk: $20.00 (1 batch has no unit cost, so the real total is higher).\n");
        // Sections come in order of urgency.
        assertThat(email.body().indexOf("PULL NOW")).isLessThan(email.body().indexOf("EXPIRES TODAY"));
        assertThat(email.body().indexOf("EXPIRES TODAY")).isLessThan(email.body().indexOf("NEXT 3 DAYS"));
        assertThat(email.body().indexOf("NEXT 3 DAYS")).isLessThan(email.body().indexOf("NEXT 7 DAYS"));
    }

    @Test
    void leavesOutEmptySectionsAndTheUnknownCostNoteWhenAllCostsAreKnown() {
        ExpiringReport report = report(List.of(
                batch("Greek yogurt", null, "2026-11-26", 4, 150, BatchStatus.ACTIVE),
                batch("Whole milk 1 gal", null, "2026-11-25", 1, 250, BatchStatus.ACTIVE)));

        EmailMessage email = DigestComposer.compose("owner@zmarket23.example", null, report);

        assertThat(email.subject()).isEqualTo("Dayton expiry digest: 2 in 3 days");
        assertThat(email.body())
                .startsWith("Good morning. Here is what expires soon at Dayton, as of")
                .doesNotContain("PULL NOW", "EXPIRES TODAY", "NEXT 7 DAYS", "no unit cost")
                .endsWith("Value at risk: $8.50.\n");
    }

    @Test
    void namesAMissingItemInsteadOfFailing() {
        ExpiringReport report = report(List.of(batch(null, null, "2026-11-24", 1, null, BatchStatus.ACTIVE)));

        assertThat(DigestComposer.compose("a@b.example", "Zmarket23", report).body())
                .contains("- Unknown item: 1 left, expires Tue Nov 24, cost unknown")
                .contains("(1 batch has no unit cost");
    }
}
