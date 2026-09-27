package co.edu.corhuila.opti.products.app;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Contract checks of the frame catalogue plus the stock rules that belong to this domain. */
class FramesHttpTest extends ContractChecks {

    @Override
    protected String collectionPath() {
        return "/api/v1/frames";
    }

    @Override
    protected String writerRole() {
        return "ADMIN";
    }

    @Override
    protected String validBody(int n) {
        return frameJson("SKU-" + n, 31_000_000, 52_000_000, 8, 2);
    }

    @Override
    protected String invalidBody() {
        return """
                {"sku":"x","brand":" ","model":"","costCents":-5,"salePriceCents":100,"stock":-1}""";
    }

    @Override
    protected List<String> invalidBodyFields() {
        return List.of("sku", "brand", "model", "costCents", "stock");
    }

    // ---- money and stock rules ------------------------------------------------------------

    @Test
    void moneyWithDecimalsIsRejected() throws Exception {
        as(create(frameJson("SKU-DEC", 31_000_000, 52_000_000, 1, 0).replace("52000000", "520000.50"),
                "key-" + UUID.randomUUID()), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("salePriceCents")));
    }

    @Test
    void salePriceBelowCostIsRejectedNamingTheField() throws Exception {
        as(create(frameJson("SKU-LOW", 52_000_000, 31_000_000, 1, 0), "key-" + UUID.randomUUID()), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("salePriceCents")));
    }

    @Test
    void duplicateSkuWithAnotherKeyIsABusinessRuleViolation() throws Exception {
        String body = frameJson("SKU-DUP-" + next(), 31_000_000, 52_000_000, 1, 0);
        as(create(body, "key-" + UUID.randomUUID()), "ADMIN").andExpect(status().isCreated());

        as(create(body, "key-" + UUID.randomUUID()), "ADMIN")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void sellerCanReadButNotCreate() throws Exception {
        as(get(collectionPath()), "SELLER").andExpect(status().isOk());
        as(create(validBody(next()), "key-" + UUID.randomUUID()), "SELLER").andExpect(status().isForbidden());
    }

    @Test
    void representationCarriesMoneyInCentsAndTheLowStockFlag() throws Exception {
        String id = registerFrame("SKU-REP-" + next(), 3, 3);

        as(get(collectionPath() + "/" + id), "SELLER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costCents").value(31_000_000))
                .andExpect(jsonPath("$.salePriceCents").value(52_000_000))
                .andExpect(jsonPath("$.stock").value(3))
                .andExpect(jsonPath("$.lowStock").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cost_cents").doesNotExist());
    }

    @Test
    void lowStockFilterAndMinStockUpdate() throws Exception {
        String sku = "SKU-MIN-" + next();
        String id = registerFrame(sku, 5, 1);

        as(get(collectionPath() + "?lowStock=true&q=" + sku), "ADMIN").andExpect(jsonPath("$.data", hasSize(0)));
        as(put(collectionPath() + "/" + id + "/min-stock").contentType(MediaType.APPLICATION_JSON)
                .content("{\"minStock\":5}"), "ADMIN")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lowStock").value(true));
        as(get(collectionPath() + "?lowStock=true&q=" + sku), "ADMIN").andExpect(jsonPath("$.data", hasSize(1)));
        as(get(collectionPath() + "?lowStock=maybe"), "ADMIN").andExpect(status().isBadRequest());
        as(put(collectionPath() + "/" + id + "/min-stock").contentType(MediaType.APPLICATION_JSON)
                .content("{\"minStock\":-1}"), "ADMIN")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("minStock"));
    }

    @Test
    void stockEntryIsIdempotentAndValidated() throws Exception {
        String id = registerFrame("SKU-ENT-" + next(), 1, 0);
        String key = "entry-" + UUID.randomUUID();

        as(entry(id, "{\"quantity\":4,\"reason\":\"purchase\"}", key), "ADMIN").andExpect(status().isCreated());
        as(entry(id, "{\"quantity\":4,\"reason\":\"purchase\"}", key), "ADMIN").andExpect(status().isOk());
        as(get(collectionPath() + "/" + id), "ADMIN").andExpect(jsonPath("$.stock").value(5));
        as(entry(id, "{\"quantity\":0}", "entry-" + UUID.randomUUID()), "ADMIN")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[*].field", hasItem("quantity")));
        as(entry(id, "{\"quantity\":1}", "short"), "ADMIN")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[*].field", hasItem("Idempotency-Key")));
    }

    // ---- reservations (used by the workflow) ----------------------------------------------

    @Test
    void reserveThenReleaseReturnsTheStockAndIsRepeatable() throws Exception {
        String id = registerFrame("SKU-RES-" + next(), 5, 0);
        String key = "reserve-" + UUID.randomUUID();

        String reservationId = idOf(as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-1\"}", key), "SERVICE")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/reservations/")))
                .andExpect(jsonPath("$.unitPriceCents").value(52_000_000))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andReturn().getResponse().getContentAsString());
        as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-1\"}", key), "SERVICE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(reservationId));
        as(get(collectionPath() + "/" + id), "ADMIN").andExpect(jsonPath("$.stock").value(3));

        as(post("/api/v1/reservations/" + reservationId + "/release"), "SERVICE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        as(post("/api/v1/reservations/" + reservationId + "/release"), "SERVICE").andExpect(status().isOk());
        as(get(collectionPath() + "/" + id), "ADMIN").andExpect(jsonPath("$.stock").value(5));
        as(get("/api/v1/reservations/" + reservationId), "SELLER")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void reservingMoreThanTheStockIsUnprocessable() throws Exception {
        String id = registerFrame("SKU-OUT-" + next(), 1, 0);

        as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-2\"}", "reserve-" + UUID.randomUUID()), "SERVICE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", containsString("insufficient stock")));
    }

    @Test
    void onlyServiceOrAdminCanReserveAndRelease() throws Exception {
        String id = registerFrame("SKU-ROL-" + next(), 5, 0);

        as(reserve(id, "{\"quantity\":1,\"reference\":\"saga-3\"}", "reserve-" + UUID.randomUUID()), "SELLER")
                .andExpect(status().isForbidden());
        as(post("/api/v1/reservations/" + UUID.randomUUID() + "/release"), "SELLER")
                .andExpect(status().isForbidden());
        as(post("/api/v1/reservations/" + UUID.randomUUID() + "/release"), "SERVICE")
                .andExpect(status().isNotFound());
    }

    @Test
    void movementsListsTheLedgerNewestFirst() throws Exception {
        String id = registerFrame("SKU-LED-" + next(), 5, 0);
        as(reserve(id, "{\"quantity\":1,\"reference\":\"saga-4\"}", "reserve-" + UUID.randomUUID()), "SERVICE")
                .andExpect(status().isCreated());

        as(get(collectionPath() + "/" + id + "/movements"), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("EXIT"))
                .andExpect(jsonPath("$.data[0].reference").value("saga-4"));
    }

    // ---- helpers --------------------------------------------------------------------------

    private String registerFrame(String sku, int stock, int minStock) throws Exception {
        return idOf(as(create(frameJson(sku, 31_000_000, 52_000_000, stock, minStock), "key-" + UUID.randomUUID()),
                "ADMIN").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder entry(String id, String body, String key) {
        return post(collectionPath() + "/" + id + "/stock-entries").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder reserve(String id, String body, String key) {
        return post(collectionPath() + "/" + id + "/reservations").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String frameJson(String sku, long cost, long price, int stock, int minStock) {
        return """
                {"sku":"%s","brand":"Ray-Ban","model":"RB5228","color":"Matte black","material":"Acetate",
                 "gender":"Unisex","costCents":%d,"salePriceCents":%d,"stock":%d,"minStock":%d,
                 "location":"Main display","supplier":"Luxottica Colombia"}""".formatted(sku, cost, price, stock, minStock);
    }
}
