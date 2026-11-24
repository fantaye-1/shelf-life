package com.shelflife.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelflife.RepositoryTest;
import com.shelflife.store.Merchant;
import com.shelflife.store.MerchantRepository;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@RepositoryTest
class ItemRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MerchantRepository merchants;

    @Autowired
    private ItemRepository items;

    private Merchant zmarket;
    private Merchant otherStore;

    @BeforeEach
    void setUp() {
        zmarket = merchants.save(new Merchant("Zmarket23"));
        otherStore = merchants.save(new Merchant("Other Store"));
    }

    @Test
    void savesAndReloadsAnItemWithBarcodeAndSku() {
        Item milk = new Item(zmarket.getId(), "Whole Milk 1 gal");
        milk.changeSku(" MILK-1G ");
        milk.changeGtin("036000291452");
        Item saved = items.save(milk);
        em.flush();
        em.clear();

        Item loaded = items.findByMerchantIdAndId(zmarket.getId(), saved.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Whole Milk 1 gal");
        assertThat(loaded.getSku()).isEqualTo("MILK-1G");
        assertThat(loaded.getGtin()).isEqualTo("036000291452");
    }

    @Test
    void findsItemsByBarcodeWithinOneMerchantOnly() {
        Item ours = new Item(zmarket.getId(), "Injera");
        ours.changeGtin("4006381333931");
        Item theirs = new Item(otherStore.getId(), "Injera (other store)");
        theirs.changeGtin("4006381333931");
        items.save(ours);
        items.save(theirs);
        em.flush();

        assertThat(items.findAllByMerchantIdAndGtin(zmarket.getId(), "4006381333931"))
                .extracting(Item::getName)
                .containsExactly("Injera");
    }

    @Test
    void oneMerchantCannotReadAnothersItem() {
        Item ours = items.save(new Item(zmarket.getId(), "Berbere 1 lb"));
        em.flush();

        assertThat(items.findByMerchantIdAndId(otherStore.getId(), ours.getId())).isEmpty();
        assertThat(items.findAllByMerchantIdOrderByNameAsc(otherStore.getId())).isEmpty();
    }

    @Test
    void squareVariationIdIsUniquePerMerchant() {
        Item first = new Item(zmarket.getId(), "Coffee");
        first.linkSquare("VAR1");
        items.save(first);
        em.flush();

        assertThat(items.findByMerchantIdAndSquareVariationId(zmarket.getId(), "VAR1")).isPresent();

        Item duplicate = new Item(zmarket.getId(), "Coffee copy");
        duplicate.linkSquare("VAR1");
        items.save(duplicate);
        assertThatThrownBy(em::flush).isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_item_square_variation");
    }

    @Test
    void databaseRejectsMalformedBarcodeEvenIfCodeIsBypassed() {
        Item item = items.save(new Item(zmarket.getId(), "Tea"));
        em.flush();

        assertThatThrownBy(() -> em.getEntityManager()
                        .createNativeQuery("UPDATE item SET gtin = '12AB' WHERE id = :id")
                        .setParameter("id", item.getId())
                        .executeUpdate())
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_item_gtin_digits");
    }

    @Test
    void entityRejectsBarcodeWithWrongCheckDigit() {
        Item item = new Item(zmarket.getId(), "Tea");
        assertThatThrownBy(() -> item.changeGtin("036000291453"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
