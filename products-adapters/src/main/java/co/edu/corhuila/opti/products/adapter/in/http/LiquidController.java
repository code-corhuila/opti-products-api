package co.edu.corhuila.opti.products.adapter.in.http;

import java.net.URI;
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

import co.edu.corhuila.opti.products.adapter.in.http.LiquidDtos.LiquidResponse;
import co.edu.corhuila.opti.products.adapter.in.http.LiquidDtos.MinStockRequest;
import co.edu.corhuila.opti.products.adapter.in.http.LiquidDtos.RegisterLiquidRequest;
import co.edu.corhuila.opti.products.adapter.in.http.LiquidDtos.ReservationResponse;
import co.edu.corhuila.opti.products.adapter.in.http.LiquidDtos.ReserveRequest;
import co.edu.corhuila.opti.products.adapter.in.http.LiquidDtos.StockEntryRequest;
import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases;
import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases.LiquidFilter;
import co.edu.corhuila.opti.products.domain.model.LiquidStatus;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP adapter of the liquid use cases. Shape and role checks here; business rules in the core.
 * Reservations live under their own {@code /liquids/reservations/...} namespace, same convention
 * as {@link LensController} and {@link AccessoryController}, never the shared
 * {@code /reservations/{id}} that {@link FrameController} owns.
 */
@RestController
@RequestMapping("/api/v1")
class LiquidController {

    private static final String LIQUIDS = "/api/v1/liquids";
    private static final String LIQUID_RESERVATIONS = "/api/v1/liquids/reservations";

    private final LiquidUseCases useCases;

    LiquidController(LiquidUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping("/liquids")
    ResponseEntity<Responses.CreatedBody> register(HttpServletRequest http,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody RegisterLiquidRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        var result = useCases.register(body.toData(), key);
        return Responses.created(result, result.value().id(), LIQUIDS);
    }

    @GetMapping("/liquids")
    PageResponse<LiquidResponse> search(HttpServletRequest http, @RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String lowStock,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "q", "status", "lowStock", "page", "limit");
        var filter = new LiquidFilter(q, parseBoolean(lowStock, "lowStock"), parseStatus(status));
        return PageResponse.of(useCases.search(filter, RequestRules.page(page, limit)).map(LiquidResponse::from));
    }

    @GetMapping("/liquids/{id}")
    LiquidResponse get(@PathVariable String id) {
        return LiquidResponse.from(useCases.get(RequestRules.uuid(id, "id")));
    }

    @PutMapping("/liquids/{id}/min-stock")
    LiquidResponse updateMinStock(HttpServletRequest http, @PathVariable String id, @RequestBody MinStockRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        return LiquidResponse.from(useCases.updateMinStock(RequestRules.uuid(id, "id"), body.minStock()));
    }

    @PostMapping("/liquids/{id}/stock-entries")
    ResponseEntity<LiquidResponse> addStock(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody StockEntryRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        UUID liquidId = RequestRules.uuid(id, "id");
        var result = useCases.addStock(liquidId, body.quantity(), key);
        return ResponseEntity.ok(LiquidResponse.from(result.value()));
    }

    @PostMapping("/liquids/{id}/reservations")
    ResponseEntity<ReservationResponse> reserve(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ReserveRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SERVICE);
        var result = useCases.reserve(RequestRules.uuid(id, "id"), body.quantity(), body.reference(), key);
        ReservationResponse response = ReservationResponse.from(result.value());
        if (!result.created()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create(LIQUID_RESERVATIONS + "/" + response.id())).body(response);
    }

    @GetMapping("/liquids/reservations/{id}")
    ReservationResponse getReservation(@PathVariable String id) {
        return ReservationResponse.from(useCases.getReservation(RequestRules.uuid(id, "id")));
    }

    /** Compensation of a reservation: idempotent, answers 200 also when it was already released. */
    @PostMapping("/liquids/reservations/{id}/release")
    ReservationResponse release(HttpServletRequest http, @PathVariable String id) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SERVICE);
        return ReservationResponse.from(useCases.release(RequestRules.uuid(id, "id")));
    }

    private static LiquidStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LiquidStatus.valueOf(value);
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
