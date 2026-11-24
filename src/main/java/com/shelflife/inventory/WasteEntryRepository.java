package com.shelflife.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Merchant-scoped access to waste records; no unscoped finders are exposed. */
public interface WasteEntryRepository extends Repository<WasteEntry, UUID> {

    WasteEntry save(WasteEntry entry);

    List<WasteEntry> findAllByMerchantIdAndBatchIdOrderByRecordedAtAsc(UUID merchantId, UUID batchId);

    /** Waste in a time window, e.g. one month, for the waste report. End is exclusive. */
    List<WasteEntry> findAllByMerchantIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
            UUID merchantId, Instant from, Instant to);
}
