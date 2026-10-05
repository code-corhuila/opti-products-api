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

import java.util.List;

import co.edu.corhuila.opti.products.adapter.in.http.FrameDtos.FrameResponse;
import co.edu.corhuila.opti.products.adapter.in.http.FrameDtos.FrameSummaryResponse;
import co.edu.corhuila.opti.products.adapter.in.http.FrameDtos.MinStockRequest;
import co.edu.corhuila.opti.products.adapter.in.http.FrameDtos.MovementResponse;
import co.edu.corhuila.opti.products.adapter.in.http.FrameDtos.RegisterFrameRequest;
import co.edu.corhuila.opti.products.adapter.in.http.FrameDtos.ReservationResponse;
import co.edu.corhuila.opti.products.adapter.in.http.FrameDtos.ReserveRequest;
import co.edu.corhuila.opti.products.adapter.in.http.FrameDtos.StockEntryRequest;
import co.edu.corhuila.opti.products.application.port.in.FrameUseCases;
import co.edu.corhuila.opti.products.application.port.in.FrameUseCases.FrameFilter;
import co.edu.corhuila.opti.products.domain.model.FrameStatus;

import jakarta.servlet.http.HttpServletRequest;

/** HTTP adapter of the frame use cases. Shape and role checks here; business rules in the core. */
@RestController
@RequestMapping("/api/v1")
class FrameController {

    private static final String FRAMES = "/api/v1/frames";

    private final FrameUseCases useCases;

    FrameController(FrameUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping("/frames")
    ResponseEntity<Responses.CreatedBody> register(HttpServletRequest http,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody RegisterFrameRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        var result = useCases.register(body.toData(), key);
        return Responses.created(result, result.value().id(), FRAMES);
    }

    @GetMapping("/frames")
    PageResponse<FrameResponse> search(HttpServletRequest http, @RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String lowStock,
            @RequestParam(required = false) String brand, @RequestParam(required = false) String page,
            @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "q", "status", "lowStock", "brand", "page", "limit");
        var filter = new FrameFilter(q, parseBoolean(lowStock, "lowStock"), parseStatus(status),
                (brand == null || brand.isBlank()) ? null : brand);
        return PageResponse.of(useCases.search(filter, RequestRules.page(page, limit)).map(FrameResponse::from));
    }

    @GetMapping("/frames/{id}")
    FrameResponse get(@PathVariable String id) {
        return FrameResponse.from(useCases.get(RequestRules.uuid(id, "id")));
    }

    @GetMapping("/frames/summary")
    FrameSummaryResponse summary() {
        return FrameSummaryResponse.from(useCases.summary());
    }

    @GetMapping("/frames/brands")
    List<String> brands() {
        return useCases.brands();
    }

    @PutMapping("/frames/{id}/min-stock")
    FrameResponse updateMinStock(HttpServletRequest http, @PathVariable String id, @RequestBody MinStockRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        return FrameResponse.from(useCases.updateMinStock(RequestRules.uuid(id, "id"), body.minStock()));
    }

    @PostMapping("/frames/{id}/stock-entries")
    ResponseEntity<Responses.CreatedBody> addStock(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody StockEntryRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN);
        UUID frameId = RequestRules.uuid(id, "id");
        var result = useCases.addStock(frameId, body.quantity(), body.reason(), key);
        return Responses.created(result, result.value().id(), FRAMES + "/" + frameId + "/movements");
    }

    @GetMapping("/frames/{id}/movements")
    PageResponse<MovementResponse> movements(HttpServletRequest http, @PathVariable String id,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "page", "limit");
        return PageResponse.of(useCases.movements(RequestRules.uuid(id, "id"), RequestRules.page(page, limit))
                .map(MovementResponse::from));
    }

    @PostMapping("/frames/{id}/reservations")
    ResponseEntity<ReservationResponse> reserve(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ReserveRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SERVICE);
        var result = useCases.reserve(RequestRules.uuid(id, "id"), body.quantity(), body.reference(), key);
        ReservationResponse response = ReservationResponse.from(result.value());
        if (!result.created()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + response.id())).body(response);
    }

    @GetMapping("/reservations/{id}")
    ReservationResponse getReservation(@PathVariable String id) {
        return ReservationResponse.from(useCases.getReservation(RequestRules.uuid(id, "id")));
    }

    /** Compensation of a reservation: idempotent, answers 200 also when it was already released. */
    @PostMapping("/reservations/{id}/release")
    ReservationResponse release(HttpServletRequest http, @PathVariable String id) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SERVICE);
        return ReservationResponse.from(useCases.release(RequestRules.uuid(id, "id")));
    }

    private static FrameStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FrameStatus.valueOf(value);
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
