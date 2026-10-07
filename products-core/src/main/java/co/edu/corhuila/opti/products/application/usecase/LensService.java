package co.edu.corhuila.opti.products.application.usecase;

import java.time.Clock;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.LensUseCases;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.application.port.out.IdGenerator;
import co.edu.corhuila.opti.products.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.products.application.port.out.LensRepository;
import co.edu.corhuila.opti.products.application.port.out.ReservationRepository;
import co.edu.corhuila.opti.products.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Lens;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.Validation;
import co.edu.corhuila.opti.products.domain.model.Violations;

/**
 * Lens catalogue and stock. Every creation claims its idempotency key first, inside the unit of
 * work, so a retry (even a concurrent one) changes nothing a second time.
 */
public class LensService implements LensUseCases {

    private static final String LENS = "LENS";
    private static final String LENS_STOCK_ENTRY = "LENS_STOCK_ENTRY";
    private static final String LENS_RESERVATION = "LENS_RESERVATION";
    private static final int MAX_REFERENCE = 64;

    private final LensRepository lenses;
    private final ReservationRepository reservations;
    private final IdempotencyStore keys;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public LensService(LensRepository lenses, ReservationRepository reservations, IdempotencyStore keys,
                       IdGenerator ids, UnitOfWork unitOfWork, Clock clock) {
        this.lenses = lenses;
        this.reservations = reservations;
        this.keys = keys;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Created<Lens> register(Lens.RegisterData data, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Lens lens = v.check(() -> Lens.register(ids.next(), data, clock.instant()));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, LENS, lens.id())) {
                return new Created<>(get(boundTo(key, LENS)), false);
            }
            if (lenses.existsBySku(lens.sku())) {
                throw DomainException.rule("a lens with this sku already exists");
            }
            lenses.insert(lens);
            return new Created<>(lens, true);
        });
    }

    @Override
    public Lens get(UUID id) {
        return lenses.findById(id).orElseThrow(() -> DomainException.notFound("lens not found"));
    }

    @Override
    public PageResult<Lens> search(LensFilter filter, PageQuery page) {
        return lenses.search(filter, page);
    }

    @Override
    public Lens updateMinStock(UUID id, Integer minStock) {
        Violations v = new Violations();
        Integer min = v.check(() -> Lens.quantity(minStock, "minStock", 0));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            Lens updated = lockedLens(id).withMinStock(min);
            lenses.update(updated);
            return updated;
        });
    }

    @Override
    public Created<Lens> addStock(UUID lensId, Integer quantity, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Lens.quantity(quantity, "quantity", 1));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, LENS_STOCK_ENTRY, lensId)) {
                return new Created<>(get(lensId), false);
            }
            Lens updated = lockedLens(lensId).restock(units);
            lenses.update(updated);
            return new Created<>(updated, true);
        });
    }

    @Override
    public Created<Reservation> reserve(UUID lensId, Integer quantity, String reference, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Lens.quantity(quantity, "quantity", 1));
        String ref = v.check(() -> Validation.text(reference, "reference", 1, MAX_REFERENCE));
        v.throwIfAny();
        UUID reservationId = ids.next();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, LENS_RESERVATION, reservationId)) {
                return new Created<>(getReservation(boundTo(key, LENS_RESERVATION)), false);
            }
            Lens lens = lockedLens(lensId);
            lenses.update(lens.reserve(units));
            Reservation reservation = Reservation.hold(reservationId, lens, units, ref, clock.instant());
            reservations.insert(reservation);
            return new Created<>(reservation, true);
        });
    }

    @Override
    public Reservation getReservation(UUID id) {
        return reservations.findById(id).orElseThrow(() -> DomainException.notFound("reservation not found"));
    }

    @Override
    public Reservation release(UUID reservationId) {
        return unitOfWork.run(() -> {
            Reservation reservation = reservations.findByIdForUpdate(reservationId)
                    .orElseThrow(() -> DomainException.notFound("reservation not found"));
            if (reservation.isReleased()) {
                return reservation;
            }
            Lens lens = lockedLens(reservation.frameId());
            lenses.update(lens.restock(reservation.quantity()));
            Reservation released = reservation.release();
            reservations.update(released);
            return released;
        });
    }

    private Lens lockedLens(UUID id) {
        return lenses.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("lens not found"));
    }

    private UUID boundTo(String key, String resourceType) {
        return keys.find(key, resourceType).orElseThrow();
    }
}
