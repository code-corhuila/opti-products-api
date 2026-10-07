package co.edu.corhuila.opti.products.application.port.in;

import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.domain.model.Accessory;
import co.edu.corhuila.opti.products.domain.model.AccessoryStatus;
import co.edu.corhuila.opti.products.domain.model.Reservation;

/** What the products service offers: the accessory catalogue, its stock and stock reservations. */
public interface AccessoryUseCases {

    Created<Accessory> register(Accessory.RegisterData data, String idempotencyKey);

    Accessory get(UUID id);

    PageResult<Accessory> search(AccessoryFilter filter, PageQuery page);

    Accessory updateMinStock(UUID id, Integer minStock);

    Created<Accessory> addStock(UUID accessoryId, Integer quantity, String idempotencyKey);

    /** Holds units for a sale. The reference (for example the saga id) is stored for traceability. */
    Created<Reservation> reserve(UUID accessoryId, Integer quantity, String reference, String idempotencyKey);

    Reservation getReservation(UUID id);

    /** Gives the units back. Safe to call more than once (compensations can be repeated). */
    Reservation release(UUID reservationId);

    /** Listing criteria; every field is optional. */
    record AccessoryFilter(String query, Boolean lowStock, AccessoryStatus status, String category) {
    }
}
