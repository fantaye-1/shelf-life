package com.shelflife.inventory;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelflife.TestcontainersConfiguration;
import com.shelflife.catalog.Item;
import com.shelflife.catalog.ItemRepository;
import com.shelflife.store.Location;
import com.shelflife.store.LocationRepository;
import com.shelflife.store.Merchant;
import com.shelflife.store.MerchantRepository;
import com.shelflife.web.CurrentMerchantResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ExpiringApiTest.FixedClock.class})
class ExpiringApiTest {

    /** 03:00 UTC on Nov 25 is 10 PM on Nov 24 in Dayton: the store's today is Nov 24. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-25T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final String HEADER = CurrentMerchantResolver.HEADER;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MerchantRepository merchants;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private ItemRepository items;

    @Autowired
    private BatchRepository batches;

    private UUID zmarket;
    private UUID otherStore;
    private UUID dayton;
    private UUID kettering;
    private UUID milk;
    private UUID yogurt;
    private UUID bread;

    @BeforeEach
    void setUp() {
        zmarket = merchants.save(new Merchant("Zmarket23")).getId();
        otherStore = merchants.save(new Merchant("Other Store")).getId();
        dayton = locations.save(new Location(zmarket, "Dayton", "America/New_York")).getId();
        kettering = locations.save(new Location(zmarket, "Kettering", "America/New_York")).getId();
        Item milkItem = new Item(zmarket, "Whole milk 1 gal");
        milkItem.changeSku("MILK-1G");
        milk = items.save(milkItem).getId();
        yogurt = items.save(new Item(zmarket, "Greek yogurt")).getId();
        bread = items.save(new Item(zmarket, "Injera pack")).getId();
    }

    private Batch batch(UUID item, UUID location, String expiresOn, int quantity, Integer unitCostCents) {
        return batches.save(new Batch(zmarket, item, location, LocalDate.parse(expiresOn), quantity, unitCostCents));
    }

    private String id(Batch batch) {
        return batch.getId().toString();
    }

    @Test
    void listsWhatExpiresSoonByTheStoresOwnDayWithValueAtRisk() throws Exception {
        Batch expired = batch(milk, dayton, "2026-11-22", 2, 250);
        // Nov 24 is "today" at the store, though the server's UTC date is already Nov 25.
        Batch today = batch(yogurt, dayton, "2026-11-24", 4, 150);
        Batch inThree = batch(bread, dayton, "2026-11-27", 10, null);
        Batch markedDown = batch(milk, dayton, "2026-11-30", 3, 300);
        markedDown.markDown();
        batches.save(markedDown);
        batch(milk, dayton, "2026-12-10", 5, 250);            // beyond 7 days
        Batch soldOut = batch(yogurt, dayton, "2026-11-25", 1, 150);
        soldOut.markSoldOut();
        batches.save(soldOut);                                // closed: not on the shelf
        batch(milk, kettering, "2026-11-24", 6, 250);         // another store

        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()).header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(dayton.toString()))
                .andExpect(jsonPath("$.locationName").value("Dayton"))
                .andExpect(jsonPath("$.storeToday").value("2026-11-24"))
                .andExpect(jsonPath("$.withinDays").value(7))
                .andExpect(jsonPath("$.batches[*].batchId").value(
                        contains(id(expired), id(today), id(inThree), id(markedDown))))
                .andExpect(jsonPath("$.batches[*].window").value(
                        contains("EXPIRED", "TODAY", "NEXT_3_DAYS", "NEXT_7_DAYS")))
                .andExpect(jsonPath("$.batches[*].daysUntilExpiry").value(contains(-2, 0, 3, 6)))
                .andExpect(jsonPath("$.batches[0].itemName").value("Whole milk 1 gal"))
                .andExpect(jsonPath("$.batches[0].sku").value("MILK-1G"))
                .andExpect(jsonPath("$.batches[0].valueAtRiskCents").value(500))
                .andExpect(jsonPath("$.batches[2].valueAtRiskCents").value(nullValue()))
                .andExpect(jsonPath("$.batches[3].status").value("MARKED_DOWN"))
                .andExpect(jsonPath("$.counts.EXPIRED").value(1))
                .andExpect(jsonPath("$.counts.TODAY").value(1))
                .andExpect(jsonPath("$.counts.NEXT_3_DAYS").value(1))
                .andExpect(jsonPath("$.counts.NEXT_7_DAYS").value(1))
                .andExpect(jsonPath("$.counts.LATER").value(0))
                // 2 x 250 + 4 x 150 + 3 x 300; the bread has no cost and is counted separately.
                .andExpect(jsonPath("$.knownValueAtRiskCents").value(2000))
                .andExpect(jsonPath("$.batchesWithUnknownCost").value(1));
    }

    @Test
    void lookAheadCanBeShortenedOrLengthened() throws Exception {
        batch(milk, dayton, "2026-11-20", 1, 250);
        batch(yogurt, dayton, "2026-11-24", 1, 150);
        batch(bread, dayton, "2026-11-25", 1, 100);
        batch(milk, dayton, "2026-12-20", 1, 250);

        // Today only: expired stock still on the shelf is always shown.
        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()).param("withinDays", "0")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches[*].window").value(contains("EXPIRED", "TODAY")));

        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()).param("withinDays", "30")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches", hasSize(4)))
                .andExpect(jsonPath("$.batches[3].window").value("LATER"))
                .andExpect(jsonPath("$.counts.LATER").value(1));
    }

    @Test
    void sameExpiryDateIsSortedByItemName() throws Exception {
        batch(milk, dayton, "2026-11-26", 1, 250);
        batch(bread, dayton, "2026-11-26", 1, 100);
        batch(yogurt, dayton, "2026-11-26", 1, 150);

        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()).header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.batches[*].itemName").value(
                        contains("Greek yogurt", "Injera pack", "Whole milk 1 gal")));
    }

    @Test
    void emptyStoreHasZeroCounts() throws Exception {
        mvc.perform(get("/api/expiring").param("locationId", kettering.toString()).header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches", hasSize(0)))
                .andExpect(jsonPath("$.counts.EXPIRED").value(0))
                .andExpect(jsonPath("$.counts.TODAY").value(0))
                .andExpect(jsonPath("$.knownValueAtRiskCents").value(0))
                .andExpect(jsonPath("$.batchesWithUnknownCost").value(0));
    }

    @Test
    void oneMerchantCannotSeeAnothersExpiringList() throws Exception {
        batch(milk, dayton, "2026-11-24", 6, 250);

        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()).header(HEADER, otherStore.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBadRequestsAsProblemJson() throws Exception {
        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()).param("withinDays", "31")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()).param("withinDays", "-1")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()).param("withinDays", "soon")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/expiring").header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/expiring").param("locationId", UUID.randomUUID().toString())
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/expiring").param("locationId", dayton.toString()))
                .andExpect(status().isUnauthorized());
    }
}
