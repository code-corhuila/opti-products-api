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
 * Contract checks of the accessory catalogue plus its own reservation rules. Same namespaced
 * reservation convention as lenses ({@code /accessories/reservations/...}).
 */
class AccessoriesHttpTest extends ContractChecks {

    @Override
    protected String collectionPath() {
        return "/api/v1/accessories";
    }

    @Override
    protected String writerRole() {
        return "ADMIN";
    }

    @Override
    protected String validBody(int n) {
        return accessoryJson("ACC-" + n, "Estuche", 5_00000, 12_00000, 30, 10);
    }

    @Override
    protected String invalidBody() {
        return """
                {"sku":"x","category":"","costCents":-5,"salePriceCents":100,"stock":-1}""";
    }

    @Override
    protected List<String> invalidBodyFields() {
        return List.of("sku", "category", "costCents", "stock");
    }

    @Test
    void sellerCanReadButNotCreate() throws Exception {
        as(get(collectionPath()), "SELLER").andExpect(status().isOk());
        as(create(validBody(next()), "key-" + UUID.randomUUID()), "SELLER").andExpect(status().isForbidden());
    }

    @Test
    void brandIsOptional() throws Exception {
        String body = """
                {"sku":"ACC-NB-%d","category":"Cordón","costCents":200000,"salePriceCents":500000,"stock":5,"minStock":1}""".formatted(next());
        as(create(body, "key-" + UUID.randomUUID()), "ADMIN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").doesNotExist());
    }

    // ---- reservations (used by the workflow) ----------------------------------------------

    @Test
    void reserveThenReleaseReturnsTheStockAndIsRepeatable() throws Exception {
        String id = registerAccessory("ACC-RES-" + next(), 5, 0);
        String key = "reserve-" + UUID.randomUUID();

        String reservationId = idOf(as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-1\"}", key), "SERVICE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPriceCents").value(1_200_000))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andReturn().getResponse().getContentAsString());
        as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-1\"}", key), "SERVICE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(reservationId));
        as(get(collectionPath() + "/" + id), "ADMIN").andExpect(jsonPath("$.stock").value(3));

        as(post("/api/v1/accessories/reservations/" + reservationId + "/release"), "SERVICE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        as(post("/api/v1/accessories/reservations/" + reservationId + "/release"), "SERVICE").andExpect(status().isOk());
        as(get(collectionPath() + "/" + id), "ADMIN").andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    void reservingMoreThanTheStockIsUnprocessable() throws Exception {
        String id = registerAccessory("ACC-OUT-" + next(), 1, 0);

        as(reserve(id, "{\"quantity\":2,\"reference\":\"saga-2\"}", "reserve-" + UUID.randomUUID()), "SERVICE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void onlyServiceOrAdminCanReserveAndRelease() throws Exception {
        String id = registerAccessory("ACC-ROL-" + next(), 5, 0);

        as(reserve(id, "{\"quantity\":1,\"reference\":\"saga-3\"}", "reserve-" + UUID.randomUUID()), "SELLER")
                .andExpect(status().isForbidden());
        as(post("/api/v1/accessories/reservations/" + UUID.randomUUID() + "/release"), "SERVICE")
                .andExpect(status().isNotFound());
    }

    // ---- helpers --------------------------------------------------------------------------

    private String registerAccessory(String sku, int stock, int minStock) throws Exception {
        return idOf(as(create(accessoryJson(sku, "Estuche", 5_00000, 12_00000, stock, minStock), "key-" + UUID.randomUUID()),
                "ADMIN").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder reserve(String id, String body, String key) {
        return post(collectionPath() + "/" + id + "/reservations").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String accessoryJson(String sku, String category, long cost, long price, int stock, int minStock) {
        return """
                {"sku":"%s","brand":"Opti","category":"%s","costCents":%d,"salePriceCents":%d,"stock":%d,"minStock":%d}""".formatted(
                sku, category, cost, price, stock, minStock);
    }
}
