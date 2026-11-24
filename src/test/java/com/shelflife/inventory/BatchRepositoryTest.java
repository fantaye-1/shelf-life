package com.shelflife.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelflife.RepositoryTest;
import com.shelflife.catalog.Item;
import com.shelflife.catalog.ItemRepository;
import com.shelflife.store.Location;
import com.shelflife.store.LocationRepository;
import com.shelflife.store.Merchant;
import com.shelflife.store.MerchantRepository;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@RepositoryTest
class BatchRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final Instant NOW = Instant.parse("2026-11-24T15:00:00Z");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MerchantRepository merchants;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private ItemRepository items;

    @Autowired
    private BatchRepository batches;

    @Autowired
    private WasteEntryRepository waste;

    private Merchant zmarket;
    private Merchant otherStore;
    private Location dayton;
    private Item milk;

    @BeforeEach
    void setUp() {
        zmarket = merchants.save(new Merchant("Zmarket23"));
        otherStore = merchants.save(new Merchant("Other Store"));
        dayton = locations.save(new Location(zmarket.getId(), "Dayton", "America/New_York"));
        milk = items.save(new Item(zmarket.getId(), "Whole Milk 1 gal"));
        em.flush();
    }

    private Batch newBatch(LocalDate expiresOn, int quantity, Integer unitCostCents) {
        return new Batch(zmarket.getId(), milk.getId(), dayton.getId(), expiresOn, quantity, unitCostCents);
    }

    @Test
    void savesAndReloadsABatch() {
        Batch saved = batches.save(newBatch(TODAY.plusDays(5), 24, 289));
        em.flush();
        em.clear();

        Batch loaded = batches.findByMerchantIdAndId(zmarket.getId(), saved.getId()).orElseThrow();
        assertThat(loaded.getExpiresOn()).isEqualTo(TODAY.plusDays(5));
        assertThat(loaded.getQuantityRemaining()).isEqualTo(24);
        assertThat(loaded.getUnitCostCents()).isEqualTo(289);
        assertThat(loaded.getStatus()).isEqualTo(BatchStatus.ACTIVE);
    }

    @Test
    void recordsWasteAndKeepsTheBatchInStep() {
        Batch batch = batches.save(newBatch(TODAY, 10, 199));
        em.flush();

        waste.save(batch.recordWaste(4, NOW));
        batches.save(batch);
        em.flush();
        em.clear();

        Batch loaded = batches.findByMerchantIdAndId(zmarket.getId(), batch.getId()).orElseThrow();
        assertThat(loaded.getQuantityRemaining()).isEqualTo(6);
        List<WasteEntry> entries = waste.findAllByMerchantIdAndBatchIdOrderByRecordedAtAsc(zmarket.getId(), batch.getId());
        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.getQuantity()).isEqualTo(4);
            assertThat(e.getLossCents()).isEqualTo(796L);
        });
        assertThat(waste.findAllByMerchantIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                zmarket.getId(), NOW, NOW.plusSeconds(1))).hasSize(1);
        assertThat(waste.findAllByMerchantIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                zmarket.getId(), NOW.plusSeconds(1), NOW.plusSeconds(60))).isEmpty();
    }

    @Test
    void listsOpenBatchesAtAStoreSoonestExpiryFirst() {
        Batch later = batches.save(newBatch(TODAY.plusDays(7), 5, null));
        Batch soonest = batches.save(newBatch(TODAY, 5, null));
        Batch soldOut = newBatch(TODAY.plusDays(1), 5, null);
        soldOut.markSoldOut();
        batches.save(soldOut);
        Batch markedDown = newBatch(TODAY.plusDays(2), 5, null);
        markedDown.markDown();
        batches.save(markedDown);
        em.flush();

        List<Batch> open = batches.findAllByMerchantIdAndLocationIdAndStatusInOrderByExpiresOnAsc(
                zmarket.getId(), dayton.getId(), EnumSet.of(BatchStatus.ACTIVE, BatchStatus.MARKED_DOWN));
        assertThat(open).extracting(Batch::getId)
                .containsExactly(soonest.getId(), markedDown.getId(), later.getId());

        assertThat(batches.findAllByMerchantIdAndItemIdOrderByExpiresOnAsc(zmarket.getId(), milk.getId()))
                .hasSize(4);
    }

    @Test
    void oneMerchantCannotReadAnothersBatchesOrWaste() {
        Batch ours = batches.save(newBatch(TODAY, 3, 100));
        em.flush();
        waste.save(ours.recordWaste(1, NOW));
        em.flush();

        assertThat(batches.findByMerchantIdAndId(otherStore.getId(), ours.getId())).isEmpty();
        assertThat(batches.findAllByMerchantIdAndItemIdOrderByExpiresOnAsc(otherStore.getId(), milk.getId())).isEmpty();
        assertThat(waste.findAllByMerchantIdAndBatchIdOrderByRecordedAtAsc(otherStore.getId(), ours.getId())).isEmpty();
    }

    @Test
    void databaseRejectsABatchForAnotherMerchantsItem() {
        // otherStore tries to log a batch against Zmarket23's item and location.
        batches.save(new Batch(otherStore.getId(), milk.getId(), dayton.getId(), TODAY, 1, null));
        assertThatThrownBy(em::flush)
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_batch_");
    }

    @Test
    void databaseRejectsRemainingAboveReceivedEvenIfCodeIsBypassed() {
        Batch batch = batches.save(newBatch(TODAY, 5, null));
        em.flush();

        assertThatThrownBy(() -> em.getEntityManager()
                        .createNativeQuery("UPDATE batch SET quantity_remaining = 6 WHERE id = :id")
                        .setParameter("id", batch.getId())
                        .executeUpdate())
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_batch_quantity_remaining");
    }

    @Test
    void databaseRejectsAClosedStatusWithStockLeft() {
        Batch batch = batches.save(newBatch(TODAY, 5, null));
        em.flush();

        assertThatThrownBy(() -> em.getEntityManager()
                        .createNativeQuery("UPDATE batch SET status = 'WASTED' WHERE id = :id")
                        .setParameter("id", batch.getId())
                        .executeUpdate())
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_batch_status_remaining");
    }
}
