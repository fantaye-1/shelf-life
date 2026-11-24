package com.shelflife.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Every lookup takes the merchant id, so one store can never load another store's locations.
 * Extends the bare {@link Repository} on purpose: no unscoped findAll/findById is exposed.
 */
public interface LocationRepository extends Repository<Location, UUID> {

    Location save(Location location);

    Optional<Location> findByMerchantIdAndId(UUID merchantId, UUID id);

    Optional<Location> findByMerchantIdAndSquareLocationId(UUID merchantId, String squareLocationId);

    List<Location> findAllByMerchantIdOrderByNameAsc(UUID merchantId);
}
