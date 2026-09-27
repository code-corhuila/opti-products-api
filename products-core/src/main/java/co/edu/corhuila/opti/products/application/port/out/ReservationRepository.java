package co.edu.corhuila.opti.products.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.domain.model.Reservation;

/** Persistence of stock reservations. */
public interface ReservationRepository {

    void insert(Reservation reservation);

    Optional<Reservation> findById(UUID id);

    /** Same, locking the row so two releases of the same reservation cannot both return the stock. */
    Optional<Reservation> findByIdForUpdate(UUID id);

    void update(Reservation reservation);
}
