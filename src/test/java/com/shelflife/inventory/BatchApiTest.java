package com.shelflife.inventory;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
@Import({TestcontainersConfiguration.class, BatchApiTest.FixedClock.class})
class BatchApiTest {

    /**
     * 03:00 UTC on Nov 25 is still the evening of Nov 24 in Dayton (America/New_York), so a batch
     * expiring on Nov 25 has one day left at the store even though the server's date is already the 25th.
     */
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

    private UUID zmarket;
    private UUID otherStore;
    private UUID dayton;
    private UUID kettering;
    private UUID milk;

    @BeforeEach
    void setUp() {
        zmarket = merchants.save(new Merchant("Zmarket23")).getId();
        otherStore = merchants.save(new Merchant("Other Store")).getId();
        dayton = locations.save(new Location(zmarket, "Dayton", "America/New_York")).getId();
        kettering = locations.save(new Location(zmarket, "Kettering", "America/New_York")).getId();
        milk = items.save(new Item(zmarket, "Whole milk 1 gal")).getId();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, UUID merchant,
            String body) {
        return request.header(HEADER, merchant.toString()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String batchBody(UUID location, String expiresOn, int quantity, Integer unitCostCents) {
        return """
                {"itemId": "%s", "locationId": "%s", "expiresOn": "%s", "quantity": %d, "unitCostCents": %s}"""
                .formatted(milk, location, expiresOn, quantity, unitCostCents);
    }

    private String logBatch(UUID location, String expiresOn, int quantity) throws Exception {
        String response = mvc.perform(json(post("/api/batches"), zmarket, batchBody(location, expiresOn, quantity, 250)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void logsABatchAndJudgesExpiryByTheStoresOwnDay() throws Exception {
        String response = mvc.perform(json(post("/api/batches"), zmarket, batchBody(dayton, "2026-11-25", 12, 289)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/batches/")))
                .andExpect(jsonPath("$.itemId").value(milk.toString()))
                .andExpect(jsonPath("$.locationId").value(dayton.toString()))
                .andExpect(jsonPath("$.expiresOn").value("2026-11-25"))
                .andExpect(jsonPath("$.daysUntilExpiry").value(1))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.quantityReceived").value(12))
                .andExpect(jsonPath("$.quantityRemaining").value(12))
                .andExpect(jsonPath("$.unitCostCents").value(289))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");

        mvc.perform(get("/api/batches/" + id).header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityRemaining").value(12))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void anAlreadyExpiredBatchCanBeLoggedAndIsFlagged() throws Exception {
        mvc.perform(json(post("/api/batches"), zmarket, batchBody(dayton, "2026-11-23", 3, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.daysUntilExpiry").value(-1))
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.unitCostCents").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void listsOpenBatchesAtOneStoreSoonestExpiryFirst() throws Exception {
        String later = logBatch(dayton, "2026-12-10", 5);
        String sooner = logBatch(dayton, "2026-11-28", 5);
        String soldOut = logBatch(dayton, "2026-11-26", 2);
        logBatch(kettering, "2026-11-27", 4);
        mvc.perform(post("/api/batches/" + soldOut + "/sold-out").header(HEADER, zmarket.toString()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/batches").param("locationId", dayton.toString()).header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(sooner))
                .andExpect(jsonPath("$[0].daysUntilExpiry").value(4))
                .andExpect(jsonPath("$[1].id").value(later));

        mvc.perform(get("/api/batches").param("locationId", dayton.toString()).param("status", "SOLD_OUT")
                        .header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(soldOut));
    }

    @Test
    void salesReduceTheBatchAndTheLastSaleClosesIt() throws Exception {
        String id = logBatch(dayton, "2026-11-30", 10);

        mvc.perform(json(post("/api/batches/" + id + "/sales"), zmarket, """
                        {"quantity": 4}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityRemaining").value(6))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(json(post("/api/batches/" + id + "/sales"), zmarket, """
                        {"quantity": 7}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("Only 6 left")));

        mvc.perform(json(post("/api/batches/" + id + "/sales"), zmarket, """
                        {"quantity": 6}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityRemaining").value(0))
                .andExpect(jsonPath("$.status").value("SOLD_OUT"));

        // A closed batch is final.
        mvc.perform(post("/api/batches/" + id + "/mark-down").header(HEADER, zmarket.toString()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void markDownKeepsUnitsAndSoldOutEmptiesTheShelf() throws Exception {
        String id = logBatch(dayton, "2026-11-26", 8);

        mvc.perform(post("/api/batches/" + id + "/mark-down").header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MARKED_DOWN"))
                .andExpect(jsonPath("$.quantityRemaining").value(8));

        mvc.perform(post("/api/batches/" + id + "/sold-out").header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.quantityRemaining").value(0));
    }

    @Test
    void correctsAMistypedExpiryDate() throws Exception {
        String id = logBatch(dayton, "2026-01-12", 6);

        mvc.perform(json(put("/api/batches/" + id + "/expires-on"), zmarket, """
                        {"expiresOn": "2026-12-01"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresOn").value("2026-12-01"))
                .andExpect(jsonPath("$.daysUntilExpiry").value(7))
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void oneMerchantCannotSeeOrChangeAnothersBatches() throws Exception {
        String id = logBatch(dayton, "2026-11-30", 10);

        mvc.perform(get("/api/batches/" + id).header(HEADER, otherStore.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/batches/" + id + "/sales"), otherStore, """
                        {"quantity": 1}"""))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/batches/" + id + "/sold-out").header(HEADER, otherStore.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/batches").param("locationId", dayton.toString()).header(HEADER, otherStore.toString()))
                .andExpect(status().isNotFound());

        // Nor log a batch against another store's item or location.
        mvc.perform(json(post("/api/batches"), otherStore, batchBody(dayton, "2026-11-30", 1, null)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/batches/" + id).header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.quantityRemaining").value(10));
    }

    @Test
    void rejectsInvalidBatchesAsProblemJson() throws Exception {
        mvc.perform(json(post("/api/batches"), zmarket, batchBody(dayton, "2026-11-30", 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(json(post("/api/batches"), zmarket, batchBody(dayton, "2026-11-30", 3, -1)))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/batches"), zmarket, batchBody(dayton, "11/30/2026", 3, null)))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/batches"), zmarket, """
                        {"itemId": "%s", "locationId": "%s", "quantity": 3}""".formatted(milk, dayton)))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/batches"), zmarket, batchBody(UUID.randomUUID(), "2026-11-30", 3, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listNeedsAKnownLocationAndValidStatuses() throws Exception {
        mvc.perform(get("/api/batches").header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/batches").param("locationId", dayton.toString()).param("status", "EATEN")
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/batches").param("locationId", UUID.randomUUID().toString())
                        .header(HEADER, zmarket.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownBatchIsNotFoundAndUnknownMerchantIsUnauthorized() throws Exception {
        mvc.perform(get("/api/batches/" + UUID.randomUUID()).header(HEADER, zmarket.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/batches").param("locationId", dayton.toString()))
                .andExpect(status().isUnauthorized());
    }
}
