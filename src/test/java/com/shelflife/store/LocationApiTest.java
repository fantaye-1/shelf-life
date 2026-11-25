package com.shelflife.store;

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
import com.shelflife.web.CurrentMerchantResolver;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LocationApiTest {

    private static final String HEADER = CurrentMerchantResolver.HEADER;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MerchantRepository merchants;

    @Autowired
    private LocationRepository locations;

    private UUID zmarket;
    private UUID otherStore;

    @BeforeEach
    void setUp() {
        zmarket = merchants.save(new Merchant("Zmarket23")).getId();
        otherStore = merchants.save(new Merchant("Other Store")).getId();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, UUID merchant,
            String body) {
        return request.header(HEADER, merchant.toString()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String createLocation(UUID merchant, String body) throws Exception {
        String response = mvc.perform(json(post("/api/locations"), merchant, body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/locations/")))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void createsALocationAndReadsItBack() throws Exception {
        String id = createLocation(zmarket, """
                {"name": " Zmarket23 Dayton ", "timezone": "America/New_York"}""");

        mvc.perform(get("/api/locations/" + id).header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Zmarket23 Dayton"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.squareLocationId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void listsLocationsByName() throws Exception {
        createLocation(zmarket, """
                {"name": "Kettering", "timezone": "America/New_York"}""");
        createLocation(zmarket, """
                {"name": "Dayton", "timezone": "America/New_York"}""");

        mvc.perform(get("/api/locations").header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Dayton"))
                .andExpect(jsonPath("$[1].name").value("Kettering"));
    }

    @Test
    void replacesNameAndTimezoneButKeepsTheSquareLink() throws Exception {
        Location linked = new Location(zmarket, "Main", "America/New_York");
        linked.linkSquare("L123SQUARE");
        UUID id = locations.save(linked).getId();

        mvc.perform(json(put("/api/locations/" + id), zmarket, """
                        {"name": "Main St", "timezone": "America/Chicago"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main St"))
                .andExpect(jsonPath("$.timezone").value("America/Chicago"))
                .andExpect(jsonPath("$.squareLocationId").value("L123SQUARE"));
    }

    @Test
    void setsChangesAndClearsTheDigestRecipient() throws Exception {
        String id = createLocation(zmarket, """
                {"name": "Dayton", "timezone": "America/New_York", "digestEmail": "owner@zmarket23.example"}""");
        mvc.perform(get("/api/locations/" + id).header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.digestEmail").value("owner@zmarket23.example"));

        mvc.perform(json(put("/api/locations/" + id), zmarket, """
                        {"name": "Dayton", "timezone": "America/New_York", "digestEmail": "manager@zmarket23.example"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digestEmail").value("manager@zmarket23.example"));

        // PUT replaces the whole location, so leaving the address out turns the digest off.
        mvc.perform(json(put("/api/locations/" + id), zmarket, """
                        {"name": "Dayton", "timezone": "America/New_York"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digestEmail").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rejectsABadDigestEmailAsProblemJson() throws Exception {
        mvc.perform(json(post("/api/locations"), zmarket, """
                        {"name": "Dayton", "timezone": "America/New_York", "digestEmail": "not-an-email"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(json(post("/api/locations"), zmarket, """
                        {"name": "Dayton", "timezone": "America/New_York", "digestEmail": "owner@localhost"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneMerchantCannotSeeOrChangeAnothersLocation() throws Exception {
        String id = createLocation(zmarket, """
                {"name": "Dayton", "timezone": "America/New_York"}""");

        mvc.perform(get("/api/locations/" + id).header(HEADER, otherStore.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(json(put("/api/locations/" + id), otherStore, """
                        {"name": "Stolen", "timezone": "America/New_York"}"""))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/locations").header(HEADER, otherStore.toString()))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/locations/" + id).header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.name").value("Dayton"));
    }

    @Test
    void rejectsUnknownOrOffsetTimezoneAsProblemJson() throws Exception {
        mvc.perform(json(post("/api/locations"), zmarket, """
                        {"name": "Shop", "timezone": "Mars/Olympus"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("time zone")));
        mvc.perform(json(post("/api/locations"), zmarket, """
                        {"name": "Shop", "timezone": "-05:00"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsMissingNameOrTimezone() throws Exception {
        mvc.perform(json(post("/api/locations"), zmarket, """
                        {"name": " ", "timezone": "America/New_York"}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/locations"), zmarket, """
                        {"name": "Shop"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownLocationIdIsNotFound() throws Exception {
        mvc.perform(get("/api/locations/" + UUID.randomUUID()).header(HEADER, zmarket.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void requestsWithoutAKnownMerchantAreUnauthorized() throws Exception {
        mvc.perform(get("/api/locations"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/locations"), UUID.randomUUID(), """
                        {"name": "Shop", "timezone": "America/New_York"}"""))
                .andExpect(status().isUnauthorized());
    }
}
