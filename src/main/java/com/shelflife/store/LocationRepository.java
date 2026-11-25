package com.shelflife.store;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every lookup takes the merchant id, so one store can never load another store's locations.
 * Extends the bare {@link Repository} on purpose: no unscoped findAll/findById is exposed.
 * The only cross-merchant methods are the digest ones below, which the background digest job uses
 * and which no API endpoint may call.
 */
public interface LocationRepository extends Repository<Location, UUID> {

    Location save(Location location);

    Optional<Location> findByMerchantIdAndId(UUID merchantId, UUID id);

    Optional<Location> findByMerchantIdAndSquareLocationId(UUID merchantId, String squareLocationId);

    List<Location> findAllByMerchantIdOrderByNameAsc(UUID merchantId);

    /** Background digest job only: every store, of any merchant, that has a digest recipient. */
    List<Location> findAllByDigestEmailIsNotNull();

    /**
     * Background digest job only. Marks today's digest as taken for this store and returns 1, or
     * returns 0 if it already ran for {@code storeToday}. A single atomic update, so two app
     * instances can't both send the same store's digest.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            update location set digest_sent_on = :storeToday
            where id = :id and (digest_sent_on is null or digest_sent_on < :storeToday)""")
    int claimDigest(@Param("id") UUID id, @Param("storeToday") LocalDate storeToday);

    /** Background digest job only. Undoes a claim when sending failed, so the next run retries. */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            update location set digest_sent_on = null
            where id = :id and digest_sent_on = :storeToday""")
    int releaseDigest(@Param("id") UUID id, @Param("storeToday") LocalDate storeToday);
}
