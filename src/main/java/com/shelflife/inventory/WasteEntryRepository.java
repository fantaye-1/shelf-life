package com.shelflife.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Merchant-scoped access to waste records; no unscoped finders are exposed. */
public interface WasteEntryRepository extends Repository<WasteEntry, UUID> {

    WasteEntry save(WasteEntry entry);

    List<WasteEntry> findAllByMerchantIdAndBatchIdOrderByRecordedAtAsc(UUID merchantId, UUID batchId);

    /** Waste in a time window, e.g. one month, for the waste report. End is exclusive. */
    List<WasteEntry> findAllByMerchantIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
            UUID merchantId, Instant from, Instant to);

    /**
     * Waste at one store in a time window, with each record's item, for the waste report. End is exclusive.
     * The join also matches on merchant, so another store's batch can never be counted.
     */
    @Query("""
            select new com.shelflife.inventory.WastedUnits(b.itemId, w.quantity, w.lossCents, w.recordedAt)
            from WasteEntry w join Batch b on b.id = w.batchId and b.merchantId = w.merchantId
            where w.merchantId = :merchantId and b.locationId = :locationId
              and w.recordedAt >= :from and w.recordedAt < :to
            order by w.recordedAt asc""")
    List<WastedUnits> findWastedUnitsAtLocation(@Param("merchantId") UUID merchantId,
            @Param("locationId") UUID locationId, @Param("from") Instant from, @Param("to") Instant to);
}
