package co.edu.corhuila.opti.products.application.port.in;

import java.util.List;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.domain.model.Frame;
import co.edu.corhuila.opti.products.domain.model.FrameStatus;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.StockMovement;

/** What the products service offers: the frame catalogue, its stock and stock reservations. */
public interface FrameUseCases {

    Created<Frame> register(Frame.RegisterData data, String idempotencyKey);

    Frame get(UUID id);

    PageResult<Frame> search(FrameFilter filter, PageQuery page);

    /** Aggregate counters for the inventory dashboard. */
    FrameSummary summary();

    /** Distinct brand names currently in the catalogue, alphabetically sorted. */
    List<String> brands();

    Frame updateMinStock(UUID id, Integer minStock);

    Created<StockMovement> addStock(UUID frameId, Integer quantity, String reason, String idempotencyKey);

    PageResult<StockMovement> movements(UUID frameId, PageQuery page);

    /** Holds units for a sale. The reference (for example the saga id) is stored for traceability. */
    Created<Reservation> reserve(UUID frameId, Integer quantity, String reference, String idempotencyKey);

    Reservation getReservation(UUID id);

    /** Gives the units back. Safe to call more than once (compensations can be repeated). */
    Reservation release(UUID reservationId);

    /** Listing criteria; every field is optional. */
    record FrameFilter(String query, Boolean lowStock, FrameStatus status, String brand) {
    }

    /**
     * Inventory dashboard counters.
     * {@code recentCount30d} is the raw count of frames created in the last 30 days: it is reported
     * as-is (for example "+N nuevas este mes") rather than turned into an invented month-over-month
     * percentage, since there is no honest baseline for that comparison yet.
     */
    record FrameSummary(long totalReferences, long lowStockCount, long outOfStockCount, long totalValueCents,
                        long recentCount30d) {
    }
}
