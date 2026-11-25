package com.shelflife.inventory;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WasteApiTest.FixedClock.class})
class WasteApiTest {

    private static final String NOW = "2026-11-25T03:00:00Z";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);
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

    private UUID zmarket;
    private UUID otherStore;
    private UUID dayton;
    private UUID milk;

    @BeforeEach
    void setUp() {
        zmarket = merchants.save(new Merchant("Zmarket23")).getId();
        otherStore = merchants.save(new Merchant("Other Store")).getId();
        dayton = locations.save(new Location(zmarket, "Dayton", "America/New_York")).getId();
        milk = items.save(new Item(zmarket, "Whole milk 1 gal")).getId();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, UUID merchant,
            String body) {
        return request.header(HEADER, merchant.toString()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String logBatch(int quantity, Integer unitCostCents) throws Exception {
        String body = """
                {"itemId": "%s", "locationId": "%s", "expiresOn": "2026-11-24", "quantity": %d, "unitCostCents": %s}"""
                .formatted(milk, dayton, quantity, unitCostCents);
        String response = mvc.perform(json(post("/api/batches"), zmarket, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private MockHttpServletRequestBuilder waste(String batchId, UUID merchant, int quantity) {
        return json(post("/api/batches/" + batchId + "/waste"), merchant, "{\"quantity\": " + quantity + "}");
    }

    @Test
    void recordsWasteWithDollarLossAndReducesTheBatch() throws Exception {
        String id = logBatch(10, 289);

        mvc.perform(waste(id, zmarket, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waste.id").exists())
                .andExpect(jsonPath("$.waste.batchId").value(id))
                .andExpect(jsonPath("$.waste.quantity").value(3))
                .andExpect(jsonPath("$.waste.lossCents").value(867))
                .andExpect(jsonPath("$.waste.recordedAt").value(NOW))
                .andExpect(jsonPath("$.batch.quantityRemaining").value(7))
                .andExpect(jsonPath("$.batch.status").value("ACTIVE"));

        mvc.perform(get("/api/batches/" + id).header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.quantityRemaining").value(7));
    }

    @Test
    void wastingTheLastUnitsClosesTheBatchForGood() throws Exception {
        String id = logBatch(4, 100);
        mvc.perform(waste(id, zmarket, 1)).andExpect(status().isCreated());

        mvc.perform(waste(id, zmarket, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batch.quantityRemaining").value(0))
                .andExpect(jsonPath("$.batch.status").value("WASTED"));

        mvc.perform(waste(id, zmarket, 1))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(get("/api/batches/" + id + "/waste").header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].quantity", containsInAnyOrder(1, 3)))
                .andExpect(jsonPath("$[*].lossCents", containsInAnyOrder(100, 300)));
    }

    @Test
    void wasteWithoutUnitCostHasUnknownLossNotZero() throws Exception {
        String id = logBatch(5, null);

        mvc.perform(waste(id, zmarket, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waste.lossCents").value(nullValue()));
    }

    @Test
    void cannotWasteMoreThanIsLeftOrFromASoldOutBatch() throws Exception {
        String id = logBatch(5, 100);
        mvc.perform(waste(id, zmarket, 6))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Only 5 left")));
        mvc.perform(waste(id, zmarket, 0)).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/batches/" + id + "/waste"), zmarket, "{}")).andExpect(status().isBadRequest());

        mvc.perform(post("/api/batches/" + id + "/sold-out").header(HEADER, zmarket.toString()))
                .andExpect(status().isOk());
        mvc.perform(waste(id, zmarket, 1)).andExpect(status().isConflict());

        mvc.perform(get("/api/batches/" + id + "/waste").header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listsWasteInATimeWindowWithTheKnownDollarTotal() throws Exception {
        String costed = logBatch(10, 250);
        String uncosted = logBatch(10, null);
        mvc.perform(waste(costed, zmarket, 2)).andExpect(status().isCreated());
        mvc.perform(waste(costed, zmarket, 1)).andExpect(status().isCreated());
        mvc.perform(waste(uncosted, zmarket, 4)).andExpect(status().isCreated());

        mvc.perform(get("/api/waste").param("from", "2026-11-01T04:00:00Z").param("to", "2026-12-01T05:00:00Z")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01T04:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-12-01T05:00:00Z"))
                .andExpect(jsonPath("$.knownLossCents").value(750))
                .andExpect(jsonPath("$.entriesWithUnknownLoss").value(1))
                .andExpect(jsonPath("$.entries.length()").value(3));

        // The window's end is exclusive.
        mvc.perform(get("/api/waste").param("from", "2026-11-01T00:00:00Z").param("to", NOW)
                        .header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.knownLossCents").value(0))
                .andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    void timeWindowMustBeValid() throws Exception {
        mvc.perform(get("/api/waste").param("from", "2026-12-01T00:00:00Z").param("to", "2026-11-01T00:00:00Z")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/waste").param("from", "2026-11-01T00:00:00Z").header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/waste").param("from", "last month").param("to", "2026-11-01T00:00:00Z")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneMerchantCannotRecordOrSeeAnothersWaste() throws Exception {
        String id = logBatch(10, 100);
        mvc.perform(waste(id, zmarket, 2)).andExpect(status().isCreated());

        mvc.perform(waste(id, otherStore, 1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/batches/" + id + "/waste").header(HEADER, otherStore.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/waste").param("from", "2026-11-01T00:00:00Z").param("to", "2026-12-01T00:00:00Z")
                        .header(HEADER, otherStore.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knownLossCents").value(0))
                .andExpect(jsonPath("$.entries.length()").value(0));

        mvc.perform(get("/api/batches/" + id).header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.quantityRemaining").value(8));
    }

    @Test
    void unknownBatchIsNotFoundAndUnknownMerchantIsUnauthorized() throws Exception {
        mvc.perform(waste(UUID.randomUUID().toString(), zmarket, 1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/waste").param("from", "2026-11-01T00:00:00Z").param("to", "2026-12-01T00:00:00Z"))
                .andExpect(status().isUnauthorized());
    }
}
