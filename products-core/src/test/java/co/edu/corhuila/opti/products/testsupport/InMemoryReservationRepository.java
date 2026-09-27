package co.edu.corhuila.opti.products.testsupport;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.out.ReservationRepository;
import co.edu.corhuila.opti.products.domain.model.Reservation;

/** Fake of the reservation store. */
public class InMemoryReservationRepository implements ReservationRepository {

    private final Map<UUID, Reservation> byId = new HashMap<>();

    @Override
    public void insert(Reservation reservation) {
        byId.put(reservation.id(), reservation);
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public void update(Reservation reservation) {
        byId.put(reservation.id(), reservation);
    }
}
