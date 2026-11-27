package com.shelflife.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import java.time.ZoneId;
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
@Import({TestcontainersConfiguration.class, WasteReportApiTest.MovableClockConfig.class})
class WasteReportApiTest {

    private static final String NOW = "2026-11-25T03:00:00Z";
    private static final String HEADER = CurrentMerchantResolver.HEADER;

    /** A clock the test can move, so waste can be recorded in different months. */
    static final class MovableClock extends Clock {
        private volatile Instant now = Instant.parse(NOW);

        void set(String instant) {
            now = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MovableClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MovableClock clock;

    @Autowired
    private MerchantRepository merchants;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private ItemRepository items;

    private UUID zmarket;
    private UUID otherStore;
    private UUID dayton;
    private UUID columbus;
    private UUID milk;
    private UUID injera;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        zmarket = merchants.save(new Merchant("Zmarket23")).getId();
        otherStore = merchants.save(new Merchant("Other Store")).getId();
        dayton = locations.save(new Location(zmarket, "Dayton", "America/New_York")).getId();
        columbus = locations.save(new Location(zmarket, "Columbus", "America/New_York")).getId();
        milk = items.save(new Item(zmarket, "Whole milk 1 gal")).getId();
        injera = items.save(new Item(zmarket, "Injera pack")).getId();
    }

    private String logBatch(UUID item, UUID location, int quantity, Integer unitCostCents) throws Exception {
        String body = """
                {"itemId": "%s", "locationId": "%s", "expiresOn": "2026-11-24", "quantity": %d, "unitCostCents": %s}"""
                .formatted(item, location, quantity, unitCostCents);
        String response = mvc.perform(post("/api/batches").header(HEADER, zmarket.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void wasteAt(String instant, String batchId, int quantity) throws Exception {
        clock.set(instant);
        mvc.perform(post("/api/batches/" + batchId + "/waste").header(HEADER, zmarket.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": " + quantity + "}"))
                .andExpect(status().isCreated());
        clock.set(NOW);
    }

    private void recordSomeWaste() throws Exception {
        String daytonMilk = logBatch(milk, dayton, 10, 250);
        String daytonInjera = logBatch(injera, dayton, 10, null);
        String columbusMilk = logBatch(milk, columbus, 10, 250);
        // 11:30 PM Oct 31 in Dayton (already November in UTC): counts as October.
        wasteAt("2026-11-01T03:30:00Z", daytonMilk, 2);
        // 12:30 AM Nov 1 in Dayton: November.
        wasteAt("2026-11-01T04:30:00Z", daytonInjera, 3);
        wasteAt(NOW, daytonMilk, 1);
        // Another store of the same merchant: never in Dayton's report.
        wasteAt(NOW, columbusMilk, 5);
    }

    @Test
    void reportsDollarsLostPerStoreMonthAndTopItems() throws Exception {
        recordSomeWaste();

        mvc.perform(get("/api/waste/report").param("locationId", dayton.toString()).param("months", "2")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(dayton.toString()))
                .andExpect(jsonPath("$.locationName").value("Dayton"))
                .andExpect(jsonPath("$.firstMonth").value("2026-10"))
                .andExpect(jsonPath("$.lastMonth").value("2026-11"))
                .andExpect(jsonPath("$.months.length()").value(2))
                .andExpect(jsonPath("$.months[0].month").value("2026-10"))
                .andExpect(jsonPath("$.months[0].unitsWasted").value(2))
                .andExpect(jsonPath("$.months[0].knownLossCents").value(500))
                .andExpect(jsonPath("$.months[0].entriesWithUnknownLoss").value(0))
                .andExpect(jsonPath("$.months[1].month").value("2026-11"))
                .andExpect(jsonPath("$.months[1].unitsWasted").value(4))
                .andExpect(jsonPath("$.months[1].knownLossCents").value(250))
                .andExpect(jsonPath("$.months[1].entriesWithUnknownLoss").value(1))
                .andExpect(jsonPath("$.unitsWasted").value(6))
                .andExpect(jsonPath("$.knownLossCents").value(750))
                .andExpect(jsonPath("$.entriesWithUnknownLoss").value(1))
                .andExpect(jsonPath("$.topItems.length()").value(2))
                .andExpect(jsonPath("$.topItems[0].itemId").value(milk.toString()))
                .andExpect(jsonPath("$.topItems[0].itemName").value("Whole milk 1 gal"))
                .andExpect(jsonPath("$.topItems[0].unitsWasted").value(3))
                .andExpect(jsonPath("$.topItems[0].knownLossCents").value(750))
                .andExpect(jsonPath("$.topItems[1].itemName").value("Injera pack"))
                .andExpect(jsonPath("$.topItems[1].knownLossCents").value(0))
                .andExpect(jsonPath("$.topItems[1].entriesWithUnknownLoss").value(1));
    }

    @Test
    void defaultsToSixMonthsEndingWithTheStoresCurrentMonth() throws Exception {
        recordSomeWaste();

        mvc.perform(get("/api/waste/report").param("locationId", dayton.toString())
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstMonth").value("2026-06"))
                .andExpect(jsonPath("$.lastMonth").value("2026-11"))
                .andExpect(jsonPath("$.months.length()").value(6))
                .andExpect(jsonPath("$.months[0].knownLossCents").value(0))
                .andExpect(jsonPath("$.knownLossCents").value(750));

        mvc.perform(get("/api/waste/report").param("locationId", dayton.toString()).param("months", "1")
                        .header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.months.length()").value(1))
                .andExpect(jsonPath("$.knownLossCents").value(250))
                .andExpect(jsonPath("$.unitsWasted").value(4));
    }

    @Test
    void monthsMustBeInRangeAndTheStoreMustExist() throws Exception {
        for (String months : new String[] {"0", "25", "six"}) {
            mvc.perform(get("/api/waste/report").param("locationId", dayton.toString()).param("months", months)
                            .header(HEADER, zmarket.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get("/api/waste/report").header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/waste/report").param("locationId", UUID.randomUUID().toString())
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void oneMerchantCannotSeeAnothersWasteReport() throws Exception {
        recordSomeWaste();

        mvc.perform(get("/api/waste/report").param("locationId", dayton.toString())
                        .header(HEADER, otherStore.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/waste/report").param("locationId", dayton.toString()))
                .andExpect(status().isUnauthorized());
    }
}
