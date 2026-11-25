package com.shelflife.inventory;

import com.shelflife.catalog.ItemRepository;
import com.shelflife.store.Location;
import com.shelflife.store.LocationRepository;
import com.shelflife.web.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch use cases. Every method takes the merchant id first and only touches that merchant's
 * batches, items and locations. The status rules live in {@link Batch}; this class only loads,
 * checks ownership and saves.
 */
@Service
@Transactional
public class BatchService {

    /** What a list shows when no status is asked for: what is still on the shelf. */
    static final Set<BatchStatus> OPEN = EnumSet.of(BatchStatus.ACTIVE, BatchStatus.MARKED_DOWN);

    private final BatchRepository batches;
    private final ItemRepository items;
    private final LocationRepository locations;
    private final Clock clock;

    public BatchService(BatchRepository batches, ItemRepository items, LocationRepository locations,
            Clock clock) {
        this.batches = batches;
        this.items = items;
        this.locations = locations;
        this.clock = clock;
    }

    public BatchResponse log(UUID merchantId, BatchRequest request) {
        // Both checks answer 404 for another store's ids, so ids of other stores are never revealed.
        items.findByMerchantIdAndId(merchantId, request.itemId())
                .orElseThrow(() -> new NotFoundException("Item", request.itemId()));
        Location location = location(merchantId, request.locationId());
        Batch batch = batches.save(new Batch(merchantId, request.itemId(), request.locationId(),
                request.expiresOn(), request.quantity(), request.unitCostCents()));
        return BatchResponse.of(batch, location.today(clock));
    }

    @Transactional(readOnly = true)
    public BatchResponse get(UUID merchantId, UUID batchId) {
        return view(merchantId, batch(merchantId, batchId));
    }

    /** Batches at one store, soonest expiry first. Without statuses, only open batches are listed. */
    @Transactional(readOnly = true)
    public List<BatchResponse> listAtLocation(UUID merchantId, UUID locationId,
            Collection<BatchStatus> statuses) {
        Location location = location(merchantId, locationId);
        Collection<BatchStatus> wanted = statuses == null || statuses.isEmpty() ? OPEN : statuses;
        LocalDate today = location.today(clock);
        return batches.findAllByMerchantIdAndLocationIdAndStatusInOrderByExpiresOnAsc(
                        merchantId, locationId, wanted)
                .stream().map(batch -> BatchResponse.of(batch, today)).toList();
    }

    public BatchResponse markDown(UUID merchantId, UUID batchId) {
        return change(merchantId, batchId, Batch::markDown);
    }

    public BatchResponse recordSale(UUID merchantId, UUID batchId, int quantity) {
        return change(merchantId, batchId, batch -> batch.recordSale(quantity));
    }

    public BatchResponse markSoldOut(UUID merchantId, UUID batchId) {
        return change(merchantId, batchId, Batch::markSoldOut);
    }

    public BatchResponse correctExpiry(UUID merchantId, UUID batchId, LocalDate expiresOn) {
        return change(merchantId, batchId, batch -> batch.correctExpiry(expiresOn));
    }

    private BatchResponse change(UUID merchantId, UUID batchId, Consumer<Batch> action) {
        Batch batch = batch(merchantId, batchId);
        action.accept(batch);
        return view(merchantId, batch);
    }

    private Batch batch(UUID merchantId, UUID batchId) {
        return batches.findByMerchantIdAndId(merchantId, batchId)
                .orElseThrow(() -> new NotFoundException("Batch", batchId));
    }

    private Location location(UUID merchantId, UUID locationId) {
        return locations.findByMerchantIdAndId(merchantId, locationId)
                .orElseThrow(() -> new NotFoundException("Location", locationId));
    }

    private BatchResponse view(UUID merchantId, Batch batch) {
        return BatchResponse.of(batch, location(merchantId, batch.getLocationId()).today(clock));
    }
}
