package co.edu.corhuila.opti.products.adapter.in.http;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.corhuila.opti.products.adapter.in.http.LensDtos.LensResponse;
import co.edu.corhuila.opti.products.adapter.in.http.LensDtos.MinStockRequest;
import co.edu.corhuila.opti.products.adapter.in.http.LensDtos.RegisterLensRequest;
import co.edu.corhuila.opti.products.adapter.in.http.LensDtos.StockEntryRequest;
import co.edu.corhuila.opti.products.application.port.in.LensUseCases;
import co.edu.corhuila.opti.products.application.port.in.LensUseCases.LensFilter;
import co.edu.corhuila.opti.products.domain.model.LensStatus;

import jakarta.servlet.http.HttpServletRequest;

/** HTTP adapter of the lens use cases. Shape and role checks here; business rules in the core. */
@RestController
@RequestMapping("/api/v1")
class LensController {

    private static final String LENSES = "/api/v1/lenses";

    private final LensUseCases useCases;

    LensController(LensUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping("/lenses")
    ResponseEntity<Responses.CreatedBody> register(HttpServletRequest http,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody RegisterLensRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        var result = useCases.register(body.toData(), key);
        return Responses.created(result, result.value().id(), LENSES);
    }

    @GetMapping("/lenses")
    PageResponse<LensResponse> search(HttpServletRequest http, @RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String lowStock,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "q", "status", "lowStock", "page", "limit");
        var filter = new LensFilter(q, parseBoolean(lowStock, "lowStock"), parseStatus(status));
        return PageResponse.of(useCases.search(filter, RequestRules.page(page, limit)).map(LensResponse::from));
    }

    @GetMapping("/lenses/{id}")
    LensResponse get(@PathVariable String id) {
        return LensResponse.from(useCases.get(RequestRules.uuid(id, "id")));
    }

    @PutMapping("/lenses/{id}/min-stock")
    LensResponse updateMinStock(HttpServletRequest http, @PathVariable String id, @RequestBody MinStockRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        return LensResponse.from(useCases.updateMinStock(RequestRules.uuid(id, "id"), body.minStock()));
    }

    @PostMapping("/lenses/{id}/stock-entries")
    ResponseEntity<LensResponse> addStock(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody StockEntryRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        UUID lensId = RequestRules.uuid(id, "id");
        var result = useCases.addStock(lensId, body.quantity(), key);
        return ResponseEntity.ok(LensResponse.from(result.value()));
    }

    private static LensStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LensStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("status", "must be ACTIVE or INACTIVE");
        }
    }

    private static Boolean parseBoolean(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!"true".equals(value) && !"false".equals(value)) {
            throw ApiException.validation(field, "must be true or false");
        }
        return Boolean.valueOf(value);
    }
}
