package com.shelflife.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final Instant NOW = Instant.parse("2026-11-24T15:00:00Z");

    private final UUID merchantId = UUID.randomUUID();

    private Batch batch(int quantity, Integer unitCostCents) {
        Batch batch = new Batch(merchantId, UUID.randomUUID(), UUID.randomUUID(), DEC_1, quantity, unitCostCents);
        giveId(batch);
        return batch;
    }

    @Test
    void newBatchIsActiveWithEverythingOnTheShelf() {
        Batch b = batch(12, 250);
        assertThat(b.getStatus()).isEqualTo(BatchStatus.ACTIVE);
        assertThat(b.getQuantityReceived()).isEqualTo(12);
        assertThat(b.getQuantityRemaining()).isEqualTo(12);
        assertThat(b.getUnitCostCents()).isEqualTo(250);
    }

    @Test
    void rejectsZeroOrNegativeQuantityAndNegativeCost() {
        assertThatThrownBy(() -> batch(0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batch(-3, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batch(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wasteRecordsDollarLossInCents() {
        Batch b = batch(10, 199);
        WasteEntry entry = b.recordWaste(3, NOW);

        assertThat(entry.getQuantity()).isEqualTo(3);
        assertThat(entry.getLossCents()).isEqualTo(597L);
        assertThat(entry.getBatchId()).isEqualTo(b.getId());
        assertThat(entry.getMerchantId()).isEqualTo(merchantId);
        assertThat(entry.getRecordedAt()).isEqualTo(NOW);
        assertThat(b.getQuantityRemaining()).isEqualTo(7);
        assertThat(b.getStatus()).isEqualTo(BatchStatus.ACTIVE);
    }

    @Test
    void wasteWithoutUnitCostHasUnknownLossNotZero() {
        WasteEntry entry = batch(5, null).recordWaste(2, NOW);
        assertThat(entry.getLossCents()).isNull();
    }

    @Test
    void wastingTheLastUnitsClosesTheBatchAsWasted() {
        Batch b = batch(4, 100);
        b.recordSale(1);
        b.recordWaste(3, NOW);
        assertThat(b.getQuantityRemaining()).isZero();
        assertThat(b.getStatus()).isEqualTo(BatchStatus.WASTED);
    }

    @Test
    void sellingTheLastUnitsClosesTheBatchAsSoldOut() {
        Batch b = batch(4, 100);
        b.recordWaste(1, NOW);
        b.recordSale(3);
        assertThat(b.getStatus()).isEqualTo(BatchStatus.SOLD_OUT);
    }

    @Test
    void markSoldOutClearsWhateverIsLeft() {
        Batch b = batch(6, null);
        b.recordSale(2);
        b.markSoldOut();
        assertThat(b.getQuantityRemaining()).isZero();
        assertThat(b.getStatus()).isEqualTo(BatchStatus.SOLD_OUT);
    }

    @Test
    void markedDownBatchCanStillSellOrBeWasted() {
        Batch b = batch(5, 300);
        b.markDown();
        b.markDown();
        assertThat(b.getStatus()).isEqualTo(BatchStatus.MARKED_DOWN);

        b.recordSale(2);
        assertThat(b.getStatus()).isEqualTo(BatchStatus.MARKED_DOWN);
        b.recordWaste(3, NOW);
        assertThat(b.getStatus()).isEqualTo(BatchStatus.WASTED);
    }

    @Test
    void cannotRemoveMoreThanIsLeft() {
        Batch b = batch(3, null);
        assertThatThrownBy(() -> b.recordWaste(4, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only 3 left");
        assertThatThrownBy(() -> b.recordSale(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> b.recordSale(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(b.getQuantityRemaining()).isEqualTo(3);
    }

    @Test
    void closedBatchCannotChange() {
        Batch b = batch(2, null);
        b.markSoldOut();
        assertThatThrownBy(b::markDown).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> b.recordSale(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> b.recordWaste(1, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> b.correctExpiry(DEC_1.plusDays(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(b::markSoldOut).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void openBatchExpiryCanBeCorrected() {
        Batch b = batch(2, null);
        b.correctExpiry(DEC_1.plusDays(5));
        assertThat(b.getExpiresOn()).isEqualTo(DEC_1.plusDays(5));
    }

    @Test
    void daysUntilExpiryIsZeroOnTheDayAndNegativeAfter() {
        Batch b = batch(1, null);
        assertThat(b.daysUntilExpiry(DEC_1.minusDays(3))).isEqualTo(3);
        assertThat(b.daysUntilExpiry(DEC_1)).isZero();
        assertThat(b.isExpired(DEC_1)).isFalse();
        assertThat(b.daysUntilExpiry(DEC_1.plusDays(1))).isEqualTo(-1);
        assertThat(b.isExpired(DEC_1.plusDays(1))).isTrue();
    }

    @Test
    void wasteNeedsASavedBatch() {
        Batch unsaved = new Batch(merchantId, UUID.randomUUID(), UUID.randomUUID(), DEC_1, 1, null);
        assertThatThrownBy(() -> unsaved.recordWaste(1, NOW)).isInstanceOf(NullPointerException.class);
        assertThat(unsaved.getQuantityRemaining()).isEqualTo(1);
    }

    /** Simulates the id JPA assigns on save, so pure unit tests don't need a database. */
    private static void giveId(Batch batch) {
        try {
            Field id = com.shelflife.common.BaseEntity.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(batch, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
