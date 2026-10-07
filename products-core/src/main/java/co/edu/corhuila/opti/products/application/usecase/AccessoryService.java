package co.edu.corhuila.opti.products.application.usecase;

import java.time.Clock;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.AccessoryUseCases;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.AccessoryRepository;
import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.application.port.out.IdGenerator;
import co.edu.corhuila.opti.products.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.products.application.port.out.ReservationRepository;
import co.edu.corhuila.opti.products.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.products.domain.model.Accessory;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.Validation;
import co.edu.corhuila.opti.products.domain.model.Violations;

/**
 * Accessory catalogue, stock and reservations. Every creation claims its idempotency key first,
 * inside the unit of work, so a retry (even a concurrent one) changes nothing a second time.
 */
public class AccessoryService implements AccessoryUseCases {

    private static final String ACCESSORY = "ACCESSORY";
    private static final String ACCESSORY_STOCK_ENTRY = "ACCESSORY_STOCK_ENTRY";
    private static final String ACCESSORY_RESERVATION = "ACCESSORY_RESERVATION";
    private static final int MAX_REFERENCE = 64;

    private final AccessoryRepository accessories;
    private final ReservationRepository reservations;
    private final IdempotencyStore keys;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AccessoryService(AccessoryRepository accessories, ReservationRepository reservations,
                            IdempotencyStore keys, IdGenerator ids, UnitOfWork unitOfWork, Clock clock) {
        this.accessories = accessories;
        this.reservations = reservations;
        this.keys = keys;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Created<Accessory> register(Accessory.RegisterData data, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Accessory accessory = v.check(() -> Accessory.register(ids.next(), data, clock.instant()));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, ACCESSORY, accessory.id())) {
                return new Created<>(get(boundTo(key, ACCESSORY)), false);
            }
            if (accessories.existsBySku(accessory.sku())) {
                throw DomainException.rule("an accessory with this sku already exists");
            }
            accessories.insert(accessory);
            return new Created<>(accessory, true);
        });
    }

    @Override
    public Accessory get(UUID id) {
        return accessories.findById(id).orElseThrow(() -> DomainException.notFound("accessory not found"));
    }

    @Override
    public PageResult<Accessory> search(AccessoryFilter filter, PageQuery page) {
        return accessories.search(filter, page);
    }

    @Override
    public Accessory updateMinStock(UUID id, Integer minStock) {
        Violations v = new Violations();
        Integer min = v.check(() -> Accessory.quantity(minStock, "minStock", 0));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            Accessory updated = lockedAccessory(id).withMinStock(min);
            accessories.update(updated);
            return updated;
        });
    }

    @Override
    public Created<Accessory> addStock(UUID accessoryId, Integer quantity, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Accessory.quantity(quantity, "quantity", 1));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, ACCESSORY_STOCK_ENTRY, accessoryId)) {
                return new Created<>(get(accessoryId), false);
            }
            Accessory updated = lockedAccessory(accessoryId).restock(units);
            accessories.update(updated);
            return new Created<>(updated, true);
        });
    }

    @Override
    public Created<Reservation> reserve(UUID accessoryId, Integer quantity, String reference, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Accessory.quantity(quantity, "quantity", 1));
        String ref = v.check(() -> Validation.text(reference, "reference", 1, MAX_REFERENCE));
        v.throwIfAny();
        UUID reservationId = ids.next();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, ACCESSORY_RESERVATION, reservationId)) {
                return new Created<>(getReservation(boundTo(key, ACCESSORY_RESERVATION)), false);
            }
            Accessory accessory = lockedAccessory(accessoryId);
            accessories.update(accessory.reserve(units));
            Reservation reservation = Reservation.hold(reservationId, accessory, units, ref, clock.instant());
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
            Accessory accessory = lockedAccessory(reservation.frameId());
            accessories.update(accessory.restock(reservation.quantity()));
            Reservation released = reservation.release();
            reservations.update(released);
            return released;
        });
    }

    private Accessory lockedAccessory(UUID id) {
        return accessories.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("accessory not found"));
    }

    private UUID boundTo(String key, String resourceType) {
        return keys.find(key, resourceType).orElseThrow();
    }
}
