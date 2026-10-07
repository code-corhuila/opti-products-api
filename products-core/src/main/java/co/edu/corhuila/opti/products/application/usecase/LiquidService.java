package co.edu.corhuila.opti.products.application.usecase;

import java.time.Clock;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.application.port.out.IdGenerator;
import co.edu.corhuila.opti.products.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.products.application.port.out.LiquidRepository;
import co.edu.corhuila.opti.products.application.port.out.ReservationRepository;
import co.edu.corhuila.opti.products.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Liquid;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.Validation;
import co.edu.corhuila.opti.products.domain.model.Violations;

/**
 * Liquid catalogue, stock and reservations. Every creation claims its idempotency key first,
 * inside the unit of work, so a retry (even a concurrent one) changes nothing a second time.
 */
public class LiquidService implements LiquidUseCases {

    private static final String LIQUID = "LIQUID";
    private static final String LIQUID_STOCK_ENTRY = "LIQUID_STOCK_ENTRY";
    private static final String LIQUID_RESERVATION = "LIQUID_RESERVATION";
    private static final int MAX_REFERENCE = 64;

    private final LiquidRepository liquids;
    private final ReservationRepository reservations;
    private final IdempotencyStore keys;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public LiquidService(LiquidRepository liquids, ReservationRepository reservations, IdempotencyStore keys,
                         IdGenerator ids, UnitOfWork unitOfWork, Clock clock) {
        this.liquids = liquids;
        this.reservations = reservations;
        this.keys = keys;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Created<Liquid> register(Liquid.RegisterData data, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Liquid liquid = v.check(() -> Liquid.register(ids.next(), data, clock.instant()));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, LIQUID, liquid.id())) {
                return new Created<>(get(boundTo(key, LIQUID)), false);
            }
            if (liquids.existsBySku(liquid.sku())) {
                throw DomainException.rule("a liquid with this sku already exists");
            }
            liquids.insert(liquid);
            return new Created<>(liquid, true);
        });
    }

    @Override
    public Liquid get(UUID id) {
        return liquids.findById(id).orElseThrow(() -> DomainException.notFound("liquid not found"));
    }

    @Override
    public PageResult<Liquid> search(LiquidFilter filter, PageQuery page) {
        return liquids.search(filter, page);
    }

    @Override
    public Liquid updateMinStock(UUID id, Integer minStock) {
        Violations v = new Violations();
        Integer min = v.check(() -> Liquid.quantity(minStock, "minStock", 0));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            Liquid updated = lockedLiquid(id).withMinStock(min);
            liquids.update(updated);
            return updated;
        });
    }

    @Override
    public Created<Liquid> addStock(UUID liquidId, Integer quantity, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Liquid.quantity(quantity, "quantity", 1));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, LIQUID_STOCK_ENTRY, liquidId)) {
                return new Created<>(get(liquidId), false);
            }
            Liquid updated = lockedLiquid(liquidId).restock(units);
            liquids.update(updated);
            return new Created<>(updated, true);
        });
    }

    @Override
    public Created<Reservation> reserve(UUID liquidId, Integer quantity, String reference, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Liquid.quantity(quantity, "quantity", 1));
        String ref = v.check(() -> Validation.text(reference, "reference", 1, MAX_REFERENCE));
        v.throwIfAny();
        UUID reservationId = ids.next();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, LIQUID_RESERVATION, reservationId)) {
                return new Created<>(getReservation(boundTo(key, LIQUID_RESERVATION)), false);
            }
            Liquid liquid = lockedLiquid(liquidId);
            liquids.update(liquid.reserve(units));
            Reservation reservation = Reservation.hold(reservationId, liquid, units, ref, clock.instant());
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
            Liquid liquid = lockedLiquid(reservation.frameId());
            liquids.update(liquid.restock(reservation.quantity()));
            Reservation released = reservation.release();
            reservations.update(released);
            return released;
        });
    }

    private Liquid lockedLiquid(UUID id) {
        return liquids.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("liquid not found"));
    }

    private UUID boundTo(String key, String resourceType) {
        return keys.find(key, resourceType).orElseThrow();
    }
}
