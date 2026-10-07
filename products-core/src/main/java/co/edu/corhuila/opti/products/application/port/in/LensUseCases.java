package co.edu.corhuila.opti.products.application.port.in;

import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.domain.model.Lens;
import co.edu.corhuila.opti.products.domain.model.LensStatus;
import co.edu.corhuila.opti.products.domain.model.Reservation;

/** What the products service offers: the lens catalogue and its stock. */
public interface LensUseCases {

    Created<Lens> register(Lens.RegisterData data, String idempotencyKey);

    Lens get(UUID id);

    PageResult<Lens> search(LensFilter filter, PageQuery page);

    Lens updateMinStock(UUID id, Integer minStock);

    Created<Lens> addStock(UUID lensId, Integer quantity, String idempotencyKey);

    /** Holds units for a sale. The reference (for example the saga id) is stored for traceability. */
    Created<Reservation> reserve(UUID lensId, Integer quantity, String reference, String idempotencyKey);

    Reservation getReservation(UUID id);

    /** Gives the units back. Safe to call more than once (compensations can be repeated). */
    Reservation release(UUID reservationId);

    /** Listing criteria; every field is optional. */
    record LensFilter(String query, Boolean lowStock, LensStatus status) {
    }
}
