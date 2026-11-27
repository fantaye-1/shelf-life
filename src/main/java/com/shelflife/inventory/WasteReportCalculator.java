package com.shelflife.inventory;

import com.shelflife.catalog.Item;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The waste report's arithmetic, kept free of Spring and the database so it is easy to test.
 * Money stays in whole cents; entries with no cost count toward units and the unknown count, never as $0.
 */
final class WasteReportCalculator {

    private WasteReportCalculator() {
    }

    /** First instant of the month at the store, e.g. 2026-11 in New York is 2026-11-01T04:00:00Z. */
    static Instant startOf(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }

    static WasteReport build(UUID locationId, String locationName, ZoneId zone, YearMonth firstMonth,
            YearMonth lastMonth, List<WastedUnits> entries, Map<UUID, Item> itemsById, int topLimit) {
        Map<YearMonth, Tally> byMonth = new LinkedHashMap<>();
        for (YearMonth m = firstMonth; !m.isAfter(lastMonth); m = m.plusMonths(1)) {
            byMonth.put(m, new Tally());
        }
        Map<UUID, Tally> byItem = new HashMap<>();
        Tally total = new Tally();

        for (WastedUnits entry : entries) {
            YearMonth month = YearMonth.from(entry.recordedAt().atZone(zone));
            Tally monthTally = byMonth.get(month);
            if (monthTally == null) {
                continue; // outside the window; the query should not return these
            }
            monthTally.add(entry);
            total.add(entry);
            byItem.computeIfAbsent(entry.itemId(), id -> new Tally()).add(entry);
        }

        List<WasteReport.Month> months = new ArrayList<>();
        byMonth.forEach((month, t) -> months.add(
                new WasteReport.Month(month, t.units, t.knownLossCents, t.unknown)));

        List<WasteReport.TopItem> top = byItem.entrySet().stream()
                .map(e -> {
                    Item item = itemsById.get(e.getKey());
                    Tally t = e.getValue();
                    return new WasteReport.TopItem(e.getKey(), item == null ? null : item.getName(),
                            item == null ? null : item.getSku(), t.units, t.knownLossCents, t.unknown);
                })
                .sorted(Comparator.comparingLong(WasteReport.TopItem::knownLossCents).reversed()
                        .thenComparing(Comparator.comparingInt(WasteReport.TopItem::unitsWasted).reversed())
                        .thenComparing(WasteReport.TopItem::itemName, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(WasteReport.TopItem::itemId))
                .limit(topLimit)
                .toList();

        return new WasteReport(locationId, locationName, firstMonth, lastMonth, months, total.units,
                total.knownLossCents, total.unknown, top);
    }

    private static final class Tally {
        int units;
        long knownLossCents;
        int unknown;

        void add(WastedUnits entry) {
            units = Math.addExact(units, entry.quantity());
            if (entry.lossCents() == null) {
                unknown++;
            } else {
                knownLossCents = Math.addExact(knownLossCents, entry.lossCents());
            }
        }
    }
}
