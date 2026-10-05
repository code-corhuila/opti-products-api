package co.edu.corhuila.opti.products.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contract checks of the lens catalogue plus its own reservation rules. Reservations use their own
 * namespace ({@code /lenses/reservations/...}), never {@code /reservations/{id}} (that one stays
 * Frame's), so both controllers can expose the same shape without colliding on the same route.
 */
class LensesHttpTest extends ContractChecks {

    @Override
    protected String collectionPath() {
        return "/api/v1/lenses";
    }

    @Override
    protected String writerRole() {
        return "ADMIN";
    }

    @Override
    protected String validBody(int n) {
        return lensJson("LNS-" + n, 8_000_000, 15_000_000, 20, 5);
    }

    @Override
    protected String invalidBody() {
        return """
                {"sku":"x","brand":" ","lensType":"UNKNOWN","costCents":-5,"salePriceCents":100,"stock":-1}""";
    }

    @Override
    protected List<String> invalidBodyFields() {
        return List.of("sku", "brand", "lensType", "costCents", "stock");
    }

    @Test
    void sellerCanReadButNotCreate() throws Exception {
        as(get(collectionPath()), "SELLER").andExpect(status().isOk());
        as(create(validBody(next()), "key-" + UUID.randomUUID()), "SELLER").andExpect(status().isForbidden());
    }

    // ---- reservations (used by the workflow) ----------------------------------------------

    @Test
    void reserveThenReleaseReturnsTheStockAndIsRepeatable() throws Exception {
        String id = registerLens("LNS-RES-" + next(), 5, 0);
        String key = "reserve-" + UUID.randomUUID();

        String reservationId = idOf(as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-1\"}", key), "SERVICE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPriceCents").value(15_000_000))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andReturn().getResponse().getContentAsString());
        as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-1\"}", key), "SERVICE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(reservationId));
        as(get(collectionPath() + "/" + id), "ADMIN").andExpect(jsonPath("$.stock").value(3));

        as(post("/api/v1/lenses/reservations/" + reservationId + "/release"), "SERVICE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        as(post("/api/v1/lenses/reservations/" + reservationId + "/release"), "SERVICE").andExpect(status().isOk());
        as(get(collectionPath() + "/" + id), "ADMIN").andExpect(jsonPath("$.stock").value(5));
        as(get("/api/v1/lenses/reservations/" + reservationId), "SELLER")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void reservingMoreThanTheStockIsUnprocessable() throws Exception {
        String id = registerLens("LNS-OUT-" + next(), 1, 0);

        as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-2\"}", "reserve-" + UUID.randomUUID()), "SERVICE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("insufficient stock")));
    }

    @Test
    void onlyServiceOrAdminCanReserveAndRelease() throws Exception {
        String id = registerLens("LNS-ROL-" + next(), 5, 0);

        as(reserve(id, "{\"quantity\":1,\"reference\":\"saga-3\"}", "reserve-" + UUID.randomUUID()), "SELLER")
                .andExpect(status().isForbidden());
        as(post("/api/v1/lenses/reservations/" + UUID.randomUUID() + "/release"), "SELLER")
                .andExpect(status().isForbidden());
        as(post("/api/v1/lenses/reservations/" + UUID.randomUUID() + "/release"), "SERVICE")
                .andExpect(status().isNotFound());
    }

    // ---- helpers --------------------------------------------------------------------------

    private String registerLens(String sku, int stock, int minStock) throws Exception {
        return idOf(as(create(lensJson(sku, 8_000_000, 15_000_000, stock, minStock), "key-" + UUID.randomUUID()),
                "ADMIN").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder reserve(String id, String body, String key) {
        return post(collectionPath() + "/" + id + "/reservations").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String lensJson(String sku, long cost, long price, int stock, int minStock) {
        return """
                {"sku":"%s","brand":"Essilor","lensType":"MONOFOCAL","material":"CR-39","coating":"Anti-reflejo",
                 "refractiveIndexX100":150,"costCents":%d,"salePriceCents":%d,"stock":%d,"minStock":%d}""".formatted(
                sku, cost, price, stock, minStock);
    }
}
