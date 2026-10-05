package co.edu.corhuila.opti.products.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Stock held for a sale. It keeps a snapshot of the frame (sku, description, price) taken when the
 * stock was reserved, so the sale is priced by this domain and not by the caller.
 */
public final class Reservation {

    private final UUID id;
    private final UUID frameId;
    private final String sku;
    private final String description;
    private final int quantity;
    private final long unitPriceCents;
    private final String reference;
    private final ReservationStatus status;
    private final Instant createdAt;

    private Reservation(UUID id, UUID frameId, String sku, String description, int quantity, long unitPriceCents,
                        String reference, ReservationStatus status, Instant createdAt) {
        this.id = id;
        this.frameId = frameId;
        this.sku = sku;
        this.description = description;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
        this.reference = reference;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Reservation hold(UUID id, Frame frame, int quantity, String reference, Instant now) {
        return new Reservation(id, frame.id(), frame.sku(), frame.description(), quantity, frame.salePriceCents(),
                reference, ReservationStatus.RESERVED, now);
    }

    /**
     * Same hold, for a lens. Reservation is product-agnostic by construction (id, sku, description
     * and price snapshot): each product type gets its own reservation table, but reuses this
     * domain class and its port instead of duplicating it.
     */
    public static Reservation hold(UUID id, Lens lens, int quantity, String reference, Instant now) {
        return new Reservation(id, lens.id(), lens.sku(), lens.description(), quantity, lens.salePriceCents(),
                reference, ReservationStatus.RESERVED, now);
    }

    public static Reservation rehydrate(UUID id, UUID frameId, String sku, String description, int quantity,
                                        long unitPriceCents, String reference, ReservationStatus status,
                                        Instant createdAt) {
        return new Reservation(id, frameId, sku, description, quantity, unitPriceCents, reference, status, createdAt);
    }

    /** Releasing twice is harmless: compensations may run more than once. */
    public Reservation release() {
        return new Reservation(id, frameId, sku, description, quantity, unitPriceCents, reference,
                ReservationStatus.RELEASED, createdAt);
    }

    public boolean isReleased() {
        return status == ReservationStatus.RELEASED;
    }

    public UUID id() {
        return id;
    }

    public UUID frameId() {
        return frameId;
    }

    public String sku() {
        return sku;
    }

    public String description() {
        return description;
    }

    public int quantity() {
        return quantity;
    }

    public long unitPriceCents() {
        return unitPriceCents;
    }

    public String reference() {
        return reference;
    }

    public ReservationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
