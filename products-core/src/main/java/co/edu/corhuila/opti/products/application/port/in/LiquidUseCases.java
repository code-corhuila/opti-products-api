package co.edu.corhuila.opti.products.application.port.in;

import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.domain.model.Liquid;
import co.edu.corhuila.opti.products.domain.model.LiquidStatus;
import co.edu.corhuila.opti.products.domain.model.Reservation;

/** What the products service offers: the liquid catalogue, its stock and stock reservations. */
public interface LiquidUseCases {

    Created<Liquid> register(Liquid.RegisterData data, String idempotencyKey);

    Liquid get(UUID id);

    PageResult<Liquid> search(LiquidFilter filter, PageQuery page);

    Liquid updateMinStock(UUID id, Integer minStock);

    Created<Liquid> addStock(UUID liquidId, Integer quantity, String idempotencyKey);

    /** Holds units for a sale. The reference (for example the saga id) is stored for traceability. */
    Created<Reservation> reserve(UUID liquidId, Integer quantity, String reference, String idempotencyKey);

    Reservation getReservation(UUID id);

    /** Gives the units back. Safe to call more than once (compensations can be repeated). */
    Reservation release(UUID reservationId);

    /** Listing criteria; every field is optional. */
    record LiquidFilter(String query, Boolean lowStock, LiquidStatus status) {
    }
}
