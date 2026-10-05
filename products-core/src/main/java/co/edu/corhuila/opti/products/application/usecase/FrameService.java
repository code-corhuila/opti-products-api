package co.edu.corhuila.opti.products.application.usecase;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.FrameUseCases;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.application.port.out.FrameImageStorage;
import co.edu.corhuila.opti.products.application.port.out.FrameRepository;
import co.edu.corhuila.opti.products.application.port.out.IdGenerator;
import co.edu.corhuila.opti.products.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.products.application.port.out.ReservationRepository;
import co.edu.corhuila.opti.products.application.port.out.StockMovementRepository;
import co.edu.corhuila.opti.products.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Frame;
import co.edu.corhuila.opti.products.domain.model.MovementType;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.StockMovement;
import co.edu.corhuila.opti.products.domain.model.Validation;
import co.edu.corhuila.opti.products.domain.model.Violations;

/**
 * Frame catalogue, stock and reservations. Every creation claims its idempotency key first,
 * inside the unit of work, so a retry (even a concurrent one) changes nothing a second time.
 */
public class FrameService implements FrameUseCases {

    private static final String FRAME = "FRAME";
    private static final String STOCK_ENTRY = "STOCK_ENTRY";
    private static final String RESERVATION = "RESERVATION";
    private static final int MAX_REFERENCE = 64;
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png");

    private final FrameRepository frames;
    private final ReservationRepository reservations;
    private final StockMovementRepository movements;
    private final IdempotencyStore keys;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final FrameImageStorage images;

    public FrameService(FrameRepository frames, ReservationRepository reservations, StockMovementRepository movements,
                        IdempotencyStore keys, IdGenerator ids, UnitOfWork unitOfWork, Clock clock,
                        FrameImageStorage images) {
        this.frames = frames;
        this.reservations = reservations;
        this.movements = movements;
        this.keys = keys;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.images = images;
    }

    @Override
    public Created<Frame> register(Frame.RegisterData data, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Frame frame = v.check(() -> Frame.register(ids.next(), data, clock.instant()));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, FRAME, frame.id())) {
                return new Created<>(get(boundTo(key, FRAME)), false);
            }
            if (frames.existsBySku(frame.sku())) {
                throw DomainException.rule("a frame with this sku already exists");
            }
            frames.insert(frame);
            return new Created<>(frame, true);
        });
    }

    @Override
    public Frame get(UUID id) {
        return frames.findById(id).orElseThrow(() -> DomainException.notFound("frame not found"));
    }

    @Override
    public PageResult<Frame> search(FrameFilter filter, PageQuery page) {
        return frames.search(filter, page);
    }

    @Override
    public FrameSummary summary() {
        return frames.summary();
    }

    @Override
    public List<String> brands() {
        return frames.brands();
    }

    @Override
    public Frame uploadImage(UUID frameId, String contentType, byte[] content) {
        String extension = IMAGE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw DomainException.validation("file", "must be a JPEG or PNG image");
        }
        if (content == null || content.length == 0) {
            throw DomainException.validation("file", "must not be empty");
        }
        if (content.length > MAX_IMAGE_BYTES) {
            throw DomainException.validation("file", "must be 2 MB or smaller");
        }
        Frame frame = get(frameId);
        String imageUrl = images.store(frameId, extension, content);
        frames.updateImage(frameId, imageUrl);
        return frame.withImageUrl(imageUrl);
    }

    @Override
    public Frame updateMinStock(UUID id, Integer minStock) {
        Violations v = new Violations();
        Integer min = v.check(() -> Frame.quantity(minStock, "minStock", 0));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            Frame updated = lockedFrame(id).withMinStock(min);
            frames.update(updated);
            return updated;
        });
    }

    @Override
    public Created<StockMovement> addStock(UUID frameId, Integer quantity, String reason, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Frame.quantity(quantity, "quantity", 1));
        String why = v.check(() -> Validation.optionalText(reason, "reason", 255));
        v.throwIfAny();
        UUID movementId = ids.next();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, STOCK_ENTRY, movementId)) {
                return new Created<>(movements.findById(boundTo(key, STOCK_ENTRY)).orElseThrow(), false);
            }
            frames.update(lockedFrame(frameId).restock(units));
            StockMovement movement = StockMovement.of(movementId, frameId, MovementType.ENTRY, units, null,
                    why == null ? "supplier entry" : why, clock.instant());
            movements.append(movement);
            return new Created<>(movement, true);
        });
    }

    @Override
    public PageResult<StockMovement> movements(UUID frameId, PageQuery page) {
        get(frameId);
        return movements.list(frameId, page);
    }

    @Override
    public Created<Reservation> reserve(UUID frameId, Integer quantity, String reference, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Frame.quantity(quantity, "quantity", 1));
        String ref = v.check(() -> Validation.text(reference, "reference", 1, MAX_REFERENCE));
        v.throwIfAny();
        UUID reservationId = ids.next();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, RESERVATION, reservationId)) {
                return new Created<>(getReservation(boundTo(key, RESERVATION)), false);
            }
            Frame frame = lockedFrame(frameId);
            frames.update(frame.reserve(units));
            Reservation reservation = Reservation.hold(reservationId, frame, units, ref, clock.instant());
            reservations.insert(reservation);
            movements.append(StockMovement.of(ids.next(), frameId, MovementType.EXIT, units, ref,
                    "reserved for a sale", clock.instant()));
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
            Frame frame = lockedFrame(reservation.frameId());
            frames.update(frame.restock(reservation.quantity()));
            Reservation released = reservation.release();
            reservations.update(released);
            movements.append(StockMovement.of(ids.next(), reservation.frameId(), MovementType.RETURN,
                    reservation.quantity(), reservation.reference(), "reservation released", clock.instant()));
            return released;
        });
    }

    private Frame lockedFrame(UUID id) {
        return frames.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("frame not found"));
    }

    private UUID boundTo(String key, String resourceType) {
        return keys.find(key, resourceType).orElseThrow();
    }
}
