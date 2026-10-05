package co.edu.corhuila.opti.products.adapter.in.http;

import java.time.Instant;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.FrameUseCases.FrameSummary;
import co.edu.corhuila.opti.products.domain.model.Frame;
import co.edu.corhuila.opti.products.domain.model.FrameStatus;
import co.edu.corhuila.opti.products.domain.model.MovementType;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.ReservationStatus;
import co.edu.corhuila.opti.products.domain.model.StockMovement;

/**
 * Request and response objects of the public contract. The domain entities are never serialized
 * directly, so renaming an internal field cannot break a client.
 */
final class FrameDtos {

    private FrameDtos() {
    }

    record RegisterFrameRequest(String sku, String brand, String model, String color, String material, String gender,
                                Long costCents, Long salePriceCents, Integer stock, Integer minStock,
                                String location, String supplier) {

        Frame.RegisterData toData() {
            return new Frame.RegisterData(sku, brand, model, color, material, gender, costCents, salePriceCents,
                    stock, minStock, location, supplier);
        }
    }

    record MinStockRequest(Integer minStock) {
    }

    record StockEntryRequest(Integer quantity, String reason) {
    }

    record ReserveRequest(Integer quantity, String reference) {
    }

    record FrameResponse(UUID id, String sku, String brand, String model, String color, String material,
                         String gender, long costCents, long salePriceCents, int stock, int minStock,
                         boolean lowStock, String location, String supplier, FrameStatus status, Instant createdAt) {

        static FrameResponse from(Frame f) {
            return new FrameResponse(f.id(), f.sku(), f.brand(), f.model(), f.color(), f.material(), f.gender(),
                    f.costCents(), f.salePriceCents(), f.stock(), f.minStock(), f.lowStock(), f.location(),
                    f.supplier(), f.status(), f.createdAt());
        }
    }

    record FrameSummaryResponse(long totalReferences, long lowStockCount, long outOfStockCount,
                                long totalValueCents, long recentCount30d) {

        static FrameSummaryResponse from(FrameSummary s) {
            return new FrameSummaryResponse(s.totalReferences(), s.lowStockCount(), s.outOfStockCount(),
                    s.totalValueCents(), s.recentCount30d());
        }
    }

    record MovementResponse(UUID id, UUID frameId, MovementType type, int quantity, String reference, String reason,
                            Instant createdAt) {

        static MovementResponse from(StockMovement m) {
            return new MovementResponse(m.id(), m.frameId(), m.type(), m.quantity(), m.reference(), m.reason(),
                    m.createdAt());
        }
    }

    record ReservationResponse(UUID id, UUID frameId, String sku, String description, int quantity,
                               long unitPriceCents, String reference, ReservationStatus status, Instant createdAt) {

        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.id(), r.frameId(), r.sku(), r.description(), r.quantity(),
                    r.unitPriceCents(), r.reference(), r.status(), r.createdAt());
        }
    }
}
