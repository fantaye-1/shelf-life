package com.shelflife.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Every lookup takes the merchant id, so one store can never load another store's batches.
 * Extends the bare {@link Repository} on purpose: no unscoped findAll/findById is exposed.
 */
public interface BatchRepository extends Repository<Batch, UUID> {

    Batch save(Batch batch);

    Optional<Batch> findByMerchantIdAndId(UUID merchantId, UUID id);

    /** All batches of one item, soonest expiry first (the order stock should leave the shelf). */
    List<Batch> findAllByMerchantIdAndItemIdOrderByExpiresOnAsc(UUID merchantId, UUID itemId);

    /** Batches at one store in the given statuses, soonest expiry first. */
    List<Batch> findAllByMerchantIdAndLocationIdAndStatusInOrderByExpiresOnAsc(
            UUID merchantId, UUID locationId, Collection<BatchStatus> statuses);
}
