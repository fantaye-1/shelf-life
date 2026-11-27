package com.shelflife.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelflife.catalog.Item;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WasteReportCalculatorTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID MERCHANT = UUID.randomUUID();
    private static final UUID MILK = UUID.randomUUID();
    private static final UUID BREAD = UUID.randomUUID();
    private static final UUID EGGS = UUID.randomUUID();
    private static final Map<UUID, Item> ITEMS = Map.of(
            MILK, new Item(MERCHANT, "Whole milk 1 gal"),
            BREAD, new Item(MERCHANT, "Injera pack"),
            EGGS, new Item(MERCHANT, "Eggs dozen"));

    private static WastedUnits waste(UUID item, int quantity, Long lossCents, String at) {
        return new WastedUnits(item, quantity, lossCents, Instant.parse(at));
    }

    private static WasteReport build(List<WastedUnits> entries, int top) {
        return WasteReportCalculator.build(STORE, "Dayton", NEW_YORK, YearMonth.of(2026, 9),
                YearMonth.of(2026, 11), entries, ITEMS, top);
    }

    @Test
    void monthStartsAtTheStoresMidnight() {
        assertThat(WasteReportCalculator.startOf(YearMonth.of(2026, 11), NEW_YORK))
                .isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
        assertThat(WasteReportCalculator.startOf(YearMonth.of(2026, 12), NEW_YORK))
                .isEqualTo(Instant.parse("2026-12-01T05:00:00Z"));
    }

    @Test
    void everyMonthIsListedOldestFirstEvenWithNoWaste() {
        WasteReport report = build(List.of(), 5);

        assertThat(report.months()).extracting(WasteReport.Month::month)
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));
        assertThat(report.months()).allSatisfy(m -> {
            assertThat(m.unitsWasted()).isZero();
            assertThat(m.knownLossCents()).isZero();
            assertThat(m.entriesWithUnknownLoss()).isZero();
        });
        assertThat(report.knownLossCents()).isZero();
        assertThat(report.topItems()).isEmpty();
    }

    @Test
    void wasteIsCountedInTheStoresMonthNotTheUtcMonth() {
        // 11:30 PM on Oct 31 in Dayton is already Nov 1 in UTC.
        WasteReport report = build(List.of(waste(MILK, 2, 500L, "2026-11-01T03:30:00Z")), 5);

        assertThat(report.months().get(1).month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(report.months().get(1).knownLossCents()).isEqualTo(500);
        assertThat(report.months().get(2).knownLossCents()).isZero();
    }

    @Test
    void unknownCostCountsUnitsButNeverAsZeroDollars() {
        WasteReport report = build(List.of(
                waste(MILK, 2, 500L, "2026-11-10T12:00:00Z"),
                waste(BREAD, 3, null, "2026-11-11T12:00:00Z"),
                waste(BREAD, 1, null, "2026-10-11T12:00:00Z")), 5);

        WasteReport.Month november = report.months().get(2);
        assertThat(november.unitsWasted()).isEqualTo(5);
        assertThat(november.knownLossCents()).isEqualTo(500);
        assertThat(november.entriesWithUnknownLoss()).isEqualTo(1);
        assertThat(report.unitsWasted()).isEqualTo(6);
        assertThat(report.knownLossCents()).isEqualTo(500);
        assertThat(report.entriesWithUnknownLoss()).isEqualTo(2);

        WasteReport.TopItem bread = report.topItems().get(1);
        assertThat(bread.itemName()).isEqualTo("Injera pack");
        assertThat(bread.unitsWasted()).isEqualTo(4);
        assertThat(bread.knownLossCents()).isZero();
        assertThat(bread.entriesWithUnknownLoss()).isEqualTo(2);
    }

    @Test
    void topItemsAreBiggestLossFirstThenMostUnitsAndAreLimited() {
        WasteReport report = build(List.of(
                waste(EGGS, 1, 400L, "2026-09-05T12:00:00Z"),
                waste(MILK, 1, 250L, "2026-10-05T12:00:00Z"),
                waste(MILK, 1, 250L, "2026-11-05T12:00:00Z"),
                waste(BREAD, 9, null, "2026-11-06T12:00:00Z")), 2);

        assertThat(report.topItems()).extracting(WasteReport.TopItem::itemName)
                .containsExactly("Whole milk 1 gal", "Eggs dozen");
        assertThat(report.topItems().get(0).knownLossCents()).isEqualTo(500);
        assertThat(report.topItems().get(0).unitsWasted()).isEqualTo(2);
    }

    @Test
    void withEqualLossTheItemWithMoreUnitsWastedComesFirst() {
        WasteReport report = build(List.of(
                waste(EGGS, 1, null, "2026-11-05T12:00:00Z"),
                waste(BREAD, 4, null, "2026-11-06T12:00:00Z")), 5);

        assertThat(report.topItems()).extracting(WasteReport.TopItem::itemId).containsExactly(BREAD, EGGS);
    }

    @Test
    void anItemThatIsGoneStillCountsWithNoName() {
        UUID gone = UUID.randomUUID();
        WasteReport report = build(List.of(waste(gone, 1, 100L, "2026-11-05T12:00:00Z")), 5);

        assertThat(report.topItems()).singleElement().satisfies(item -> {
            assertThat(item.itemId()).isEqualTo(gone);
            assertThat(item.itemName()).isNull();
            assertThat(item.knownLossCents()).isEqualTo(100);
        });
    }
}
