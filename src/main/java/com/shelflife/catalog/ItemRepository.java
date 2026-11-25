package com.shelflife.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Every lookup takes the merchant id, so one store can never load another store's items.
 * Extends the bare {@link Repository} on purpose: no unscoped findAll/findById is exposed.
 */
public interface ItemRepository extends Repository<Item, UUID> {

    Item save(Item item);

    Optional<Item> findByMerchantIdAndId(UUID merchantId, UUID id);

    Optional<Item> findByMerchantIdAndSquareVariationId(UUID merchantId, String squareVariationId);

    /** For barcode scanning. A barcode can match more than one item, e.g. a product entered twice. */
    List<Item> findAllByMerchantIdAndGtin(UUID merchantId, String gtin);

    List<Item> findAllByMerchantIdOrderByNameAsc(UUID merchantId);

    /** Several items at once, e.g. to show names on a list of batches. */
    List<Item> findAllByMerchantIdAndIdIn(UUID merchantId, Collection<UUID> ids);
}
