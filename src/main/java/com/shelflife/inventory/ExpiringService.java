package com.shelflife.inventory;

import com.shelflife.catalog.Item;
import com.shelflife.catalog.ItemRepository;
import com.shelflife.store.Location;
import com.shelflife.store.LocationRepository;
import com.shelflife.web.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "What expires soon at this store?" Judged by the store's own calendar day, so a batch expiring
 * today in Dayton is still "today" at 10 PM there even though the server's UTC date has moved on.
 * The daily digest (next M3 slice) reuses this.
 */
@Service
@Transactional(readOnly = true)
public class ExpiringService {

    public static final int DEFAULT_WITHIN_DAYS = 7;
    public static final int MAX_WITHIN_DAYS = 30;

    private final BatchRepository batches;
    private final ItemRepository items;
    private final LocationRepository locations;
    private final Clock clock;

    public ExpiringService(BatchRepository batches, ItemRepository items, LocationRepository locations,
            Clock clock) {
        this.batches = batches;
        this.items = items;
        this.locations = locations;
        this.clock = clock;
    }

    /**
     * Open batches at one store expiring within {@code withinDays} of the store's today, plus any
     * already-expired batch still on the shelf. Soonest expiry first, then item name.
     */
    public ExpiringReport report(UUID merchantId, UUID locationId, int withinDays) {
        if (withinDays < 0 || withinDays > MAX_WITHIN_DAYS) {
            throw new IllegalArgumentException("withinDays must be between 0 and " + MAX_WITHIN_DAYS);
        }
        Location location = locations.findByMerchantIdAndId(merchantId, locationId)
                .orElseThrow(() -> new NotFoundException("Location", locationId));
        LocalDate today = location.today(clock);

        List<Batch> found = batches
                .findAllByMerchantIdAndLocationIdAndStatusInAndExpiresOnLessThanEqualOrderByExpiresOnAsc(
                        merchantId, locationId, BatchService.OPEN, today.plusDays(withinDays));

        Set<UUID> itemIds = found.stream().map(Batch::getItemId).collect(Collectors.toSet());
        Map<UUID, Item> itemsById = itemIds.isEmpty()
                ? Map.of()
                : items.findAllByMerchantIdAndIdIn(merchantId, itemIds).stream()
                        .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<ExpiringBatch> listed = found.stream()
                .map(batch -> {
                    Item item = itemsById.get(batch.getItemId());
                    return ExpiringBatch.of(batch, item == null ? null : item.getName(),
                            item == null ? null : item.getSku(), today);
                })
                .sorted(Comparator.comparing(ExpiringBatch::expiresOn)
                        .thenComparing(ExpiringBatch::itemName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<ExpiryWindow, Long> counts = new EnumMap<>(ExpiryWindow.class);
        for (ExpiryWindow window : ExpiryWindow.values()) {
            counts.put(window, 0L);
        }
        long knownValue = 0;
        int unknownCost = 0;
        for (ExpiringBatch batch : listed) {
            counts.merge(batch.window(), 1L, Long::sum);
            if (batch.valueAtRiskCents() == null) {
                unknownCost++;
            } else {
                knownValue = Math.addExact(knownValue, batch.valueAtRiskCents());
            }
        }
        return new ExpiringReport(location.getId(), location.getName(), today, withinDays, counts, knownValue,
                unknownCost, listed);
    }
}
