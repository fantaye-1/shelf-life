package com.shelflife.inventory;

import com.shelflife.catalog.Item;
import com.shelflife.catalog.ItemRepository;
import com.shelflife.store.Location;
import com.shelflife.store.LocationRepository;
import com.shelflife.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Waste use cases. Every method takes the merchant id first and only touches that merchant's
 * batches and waste. The quantity and dollar-loss rules live in {@link Batch#recordWaste}.
 */
@Service
@Transactional
public class WasteService {

    public static final int DEFAULT_REPORT_MONTHS = 6;
    public static final int MAX_REPORT_MONTHS = 24;
    public static final int TOP_ITEMS = 5;

    private final BatchRepository batches;
    private final WasteEntryRepository waste;
    private final LocationRepository locations;
    private final ItemRepository items;
    private final Clock clock;

    public WasteService(BatchRepository batches, WasteEntryRepository waste, LocationRepository locations,
            ItemRepository items, Clock clock) {
        this.batches = batches;
        this.waste = waste;
        this.locations = locations;
        this.items = items;
        this.clock = clock;
    }

    /** Throws units of a batch away, recording the dollar loss. The batch and the record change together. */
    public WasteRecordedResponse record(UUID merchantId, UUID batchId, int quantity) {
        Batch batch = batch(merchantId, batchId);
        WasteEntry entry = waste.save(batch.recordWaste(quantity, clock.instant()));
        Location location = locations.findByMerchantIdAndId(merchantId, batch.getLocationId())
                .orElseThrow(() -> new NotFoundException("Location", batch.getLocationId()));
        return new WasteRecordedResponse(WasteResponse.of(entry), BatchResponse.of(batch, location.today(clock)));
    }

    /** Every waste record of one batch, oldest first. */
    @Transactional(readOnly = true)
    public List<WasteResponse> listForBatch(UUID merchantId, UUID batchId) {
        batch(merchantId, batchId);
        return waste.findAllByMerchantIdAndBatchIdOrderByRecordedAtAsc(merchantId, batchId)
                .stream().map(WasteResponse::of).toList();
    }

    /** Waste recorded from {@code from} (inclusive) to {@code to} (exclusive), oldest first, with totals. */
    @Transactional(readOnly = true)
    public WasteListResponse listBetween(UUID merchantId, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        List<WasteEntry> entries = waste
                .findAllByMerchantIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                        merchantId, from, to);
        long knownLoss = 0;
        int unknown = 0;
        for (WasteEntry entry : entries) {
            if (entry.getLossCents() == null) {
                unknown++;
            } else {
                knownLoss = Math.addExact(knownLoss, entry.getLossCents());
            }
        }
        return new WasteListResponse(from, to, knownLoss, unknown,
                entries.stream().map(WasteResponse::of).toList());
    }

    /**
     * The waste report for one store: dollars lost per calendar month for the last {@code months}
     * months (this month included), judged by the store's own time zone, and the most wasted items.
     */
    @Transactional(readOnly = true)
    public WasteReport report(UUID merchantId, UUID locationId, int months) {
        if (months < 1 || months > MAX_REPORT_MONTHS) {
            throw new IllegalArgumentException("months must be between 1 and " + MAX_REPORT_MONTHS);
        }
        Location location = locations.findByMerchantIdAndId(merchantId, locationId)
                .orElseThrow(() -> new NotFoundException("Location", locationId));
        ZoneId zone = location.zone();
        YearMonth lastMonth = YearMonth.from(location.today(clock));
        YearMonth firstMonth = lastMonth.minusMonths(months - 1L);

        List<WastedUnits> entries = waste.findWastedUnitsAtLocation(merchantId, locationId,
                WasteReportCalculator.startOf(firstMonth, zone),
                WasteReportCalculator.startOf(lastMonth.plusMonths(1), zone));

        Set<UUID> itemIds = entries.stream().map(WastedUnits::itemId).collect(Collectors.toSet());
        Map<UUID, Item> itemsById = itemIds.isEmpty()
                ? Map.of()
                : items.findAllByMerchantIdAndIdIn(merchantId, itemIds).stream()
                        .collect(Collectors.toMap(Item::getId, Function.identity()));

        return WasteReportCalculator.build(location.getId(), location.getName(), zone, firstMonth, lastMonth,
                entries, itemsById, TOP_ITEMS);
    }

    private Batch batch(UUID merchantId, UUID batchId) {
        return batches.findByMerchantIdAndId(merchantId, batchId)
                .orElseThrow(() -> new NotFoundException("Batch", batchId));
    }
}
