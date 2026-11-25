package com.shelflife.inventory;

import com.shelflife.store.Location;
import com.shelflife.store.LocationRepository;
import com.shelflife.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Waste use cases. Every method takes the merchant id first and only touches that merchant's
 * batches and waste. The quantity and dollar-loss rules live in {@link Batch#recordWaste}.
 */
@Service
@Transactional
public class WasteService {

    private final BatchRepository batches;
    private final WasteEntryRepository waste;
    private final LocationRepository locations;
    private final Clock clock;

    public WasteService(BatchRepository batches, WasteEntryRepository waste, LocationRepository locations,
            Clock clock) {
        this.batches = batches;
        this.waste = waste;
        this.locations = locations;
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

    private Batch batch(UUID merchantId, UUID batchId) {
        return batches.findByMerchantIdAndId(merchantId, batchId)
                .orElseThrow(() -> new NotFoundException("Batch", batchId));
    }
}
