package com.shelflife.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelflife.RepositoryTest;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@RepositoryTest
class LocationRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MerchantRepository merchants;

    @Autowired
    private LocationRepository locations;

    private Merchant zmarket;
    private Merchant otherStore;

    @BeforeEach
    void setUp() {
        zmarket = merchants.save(new Merchant("Zmarket23"));
        otherStore = merchants.save(new Merchant("Other Store"));
    }

    @Test
    void savesAndReloadsALocation() {
        Location saved = locations.save(new Location(zmarket.getId(), "Dayton", "America/New_York"));
        em.flush();
        em.clear();

        Location loaded = locations.findByMerchantIdAndId(zmarket.getId(), saved.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Dayton");
        assertThat(loaded.getTimezone()).isEqualTo("America/New_York");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void oneMerchantCannotReadAnothersLocation() {
        Location dayton = locations.save(new Location(zmarket.getId(), "Dayton", "America/New_York"));
        locations.save(new Location(otherStore.getId(), "Columbus", "America/New_York"));
        em.flush();

        assertThat(locations.findByMerchantIdAndId(otherStore.getId(), dayton.getId())).isEmpty();
        assertThat(locations.findAllByMerchantIdOrderByNameAsc(otherStore.getId()))
                .extracting(Location::getName)
                .containsExactly("Columbus");
    }

    @Test
    void listsLocationsByName() {
        locations.save(new Location(zmarket.getId(), "Xenia", "America/New_York"));
        locations.save(new Location(zmarket.getId(), "Beavercreek", "America/New_York"));
        em.flush();

        assertThat(locations.findAllByMerchantIdOrderByNameAsc(zmarket.getId()))
                .extracting(Location::getName)
                .containsExactly("Beavercreek", "Xenia");
    }

    @Test
    void squareLocationIdIsUniquePerMerchant() {
        Location first = new Location(zmarket.getId(), "Dayton", "America/New_York");
        first.linkSquare("L123");
        Location sameIdOtherMerchant = new Location(otherStore.getId(), "Elsewhere", "America/New_York");
        sameIdOtherMerchant.linkSquare("L123");
        locations.save(first);
        locations.save(sameIdOtherMerchant);
        em.flush();

        assertThat(locations.findByMerchantIdAndSquareLocationId(zmarket.getId(), "L123"))
                .map(Location::getName)
                .contains("Dayton");

        Location duplicate = new Location(zmarket.getId(), "Dayton again", "America/New_York");
        duplicate.linkSquare("L123");
        locations.save(duplicate);
        assertThatThrownBy(em::flush).isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_location_square_id");
    }
}
