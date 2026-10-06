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

import co.edu.corhuila.opti.products.adapter.in.http.AccessoryDtos.AccessoryResponse;
import co.edu.corhuila.opti.products.adapter.in.http.AccessoryDtos.MinStockRequest;
import co.edu.corhuila.opti.products.adapter.in.http.AccessoryDtos.RegisterAccessoryRequest;
import co.edu.corhuila.opti.products.adapter.in.http.AccessoryDtos.ReservationResponse;
import co.edu.corhuila.opti.products.adapter.in.http.AccessoryDtos.ReserveRequest;
import co.edu.corhuila.opti.products.adapter.in.http.AccessoryDtos.StockEntryRequest;
import co.edu.corhuila.opti.products.application.port.in.AccessoryUseCases;
import co.edu.corhuila.opti.products.application.port.in.AccessoryUseCases.AccessoryFilter;
import co.edu.corhuila.opti.products.domain.model.AccessoryStatus;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP adapter of the accessory use cases. Shape and role checks here; business rules in the
 * core. Reservations live under their own {@code /accessories/reservations/...} namespace, same
 * convention as {@link LensController}, never the shared {@code /reservations/{id}} that
 * {@link FrameController} owns.
 */
@RestController
@RequestMapping("/api/v1")
class AccessoryController {

    private static final String ACCESSORIES = "/api/v1/accessories";
    private static final String ACCESSORY_RESERVATIONS = "/api/v1/accessories/reservations";

    private final AccessoryUseCases useCases;

    AccessoryController(AccessoryUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping("/accessories")
    ResponseEntity<Responses.CreatedBody> register(HttpServletRequest http,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody RegisterAccessoryRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        var result = useCases.register(body.toData(), key);
        return Responses.created(result, result.value().id(), ACCESSORIES);
    }

    @GetMapping("/accessories")
    PageResponse<AccessoryResponse> search(HttpServletRequest http, @RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String lowStock,
            @RequestParam(required = false) String category, @RequestParam(required = false) String page,
            @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "q", "status", "lowStock", "category", "page", "limit");
        var filter = new AccessoryFilter(q, parseBoolean(lowStock, "lowStock"), parseStatus(status),
                (category == null || category.isBlank()) ? null : category);
        return PageResponse.of(useCases.search(filter, RequestRules.page(page, limit)).map(AccessoryResponse::from));
    }

    @GetMapping("/accessories/{id}")
    AccessoryResponse get(@PathVariable String id) {
        return AccessoryResponse.from(useCases.get(RequestRules.uuid(id, "id")));
    }

    @PutMapping("/accessories/{id}/min-stock")
    AccessoryResponse updateMinStock(HttpServletRequest http, @PathVariable String id, @RequestBody MinStockRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        return AccessoryResponse.from(useCases.updateMinStock(RequestRules.uuid(id, "id"), body.minStock()));
    }

    @PostMapping("/accessories/{id}/stock-entries")
    ResponseEntity<AccessoryResponse> addStock(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody StockEntryRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        UUID accessoryId = RequestRules.uuid(id, "id");
        var result = useCases.addStock(accessoryId, body.quantity(), key);
        return ResponseEntity.ok(AccessoryResponse.from(result.value()));
    }

    @PostMapping("/accessories/{id}/reservations")
    ResponseEntity<ReservationResponse> reserve(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ReserveRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SERVICE);
        var result = useCases.reserve(RequestRules.uuid(id, "id"), body.quantity(), body.reference(), key);
        ReservationResponse response = ReservationResponse.from(result.value());
        if (!result.created()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create(ACCESSORY_RESERVATIONS + "/" + response.id())).body(response);
    }

    @GetMapping("/accessories/reservations/{id}")
    ReservationResponse getReservation(@PathVariable String id) {
        return ReservationResponse.from(useCases.getReservation(RequestRules.uuid(id, "id")));
    }

    /** Compensation of a reservation: idempotent, answers 200 also when it was already released. */
    @PostMapping("/accessories/reservations/{id}/release")
    ReservationResponse release(HttpServletRequest http, @PathVariable String id) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SERVICE);
        return ReservationResponse.from(useCases.release(RequestRules.uuid(id, "id")));
    }

    private static AccessoryStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AccessoryStatus.valueOf(value);
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
