package com.shelflife.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shelflife.TestcontainersConfiguration;
import com.shelflife.store.Merchant;
import com.shelflife.store.MerchantRepository;
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
class ItemApiTest {

    private static final String HEADER = CurrentMerchantResolver.HEADER;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MerchantRepository merchants;

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

    private String createItem(UUID merchant, String body) throws Exception {
        String response = mvc.perform(json(post("/api/items"), merchant, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void createsAnItemAndReadsItBack() throws Exception {
        String id = createItem(zmarket, """
                {"name": " Whole Milk 1 gal ", "sku": "MILK-1G", "gtin": "0360-0029-1452"}""");

        mvc.perform(get("/api/items/" + id).header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Whole Milk 1 gal"))
                .andExpect(jsonPath("$.sku").value("MILK-1G"))
                .andExpect(jsonPath("$.gtin").value("036000291452"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createReturnsLocationOfTheNewItem() throws Exception {
        mvc.perform(json(post("/api/items"), zmarket, """
                        {"name": "Injera"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/items/")));
    }

    @Test
    void listsItemsByNameAndFindsByBarcode() throws Exception {
        createItem(zmarket, """
                {"name": "Teff flour", "gtin": "4006381333931"}""");
        createItem(zmarket, """
                {"name": "Berbere"}""");

        mvc.perform(get("/api/items").header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Berbere"))
                .andExpect(jsonPath("$[1].name").value("Teff flour"));

        mvc.perform(get("/api/items").param("gtin", "4006381333931").header(HEADER, zmarket.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Teff flour"));
    }

    @Test
    void replacesAnItemAndClearsBlankFields() throws Exception {
        String id = createItem(zmarket, """
                {"name": "Coffee", "sku": "COF-1", "gtin": "036000291452"}""");

        mvc.perform(json(put("/api/items/" + id), zmarket, """
                        {"name": "Yirgacheffe coffee 1 lb", "sku": " ", "gtin": null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yirgacheffe coffee 1 lb"))
                .andExpect(jsonPath("$.sku").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.gtin").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void oneMerchantCannotSeeOrChangeAnothersItem() throws Exception {
        String id = createItem(zmarket, """
                {"name": "Shiro"}""");

        mvc.perform(get("/api/items/" + id).header(HEADER, otherStore.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(json(put("/api/items/" + id), otherStore, """
                        {"name": "Stolen"}"""))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/items").header(HEADER, otherStore.toString()))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/items/" + id).header(HEADER, zmarket.toString()))
                .andExpect(jsonPath("$.name").value("Shiro"));
    }

    @Test
    void rejectsBarcodeWithWrongCheckDigitAsProblemJson() throws Exception {
        mvc.perform(json(post("/api/items"), zmarket, """
                        {"name": "Tea", "gtin": "036000291453"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("check digit")));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mvc.perform(json(post("/api/items"), zmarket, """
                        {"name": "  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(json(post("/api/items"), zmarket, "{\"name\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownItemIdIsNotFound() throws Exception {
        mvc.perform(get("/api/items/" + UUID.randomUUID()).header(HEADER, zmarket.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void malformedItemIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/items/not-a-uuid").header(HEADER, zmarket.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestsWithoutAKnownMerchantAreUnauthorized() throws Exception {
        mvc.perform(get("/api/items"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/items").header(HEADER, "garbage"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/items").header(HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
