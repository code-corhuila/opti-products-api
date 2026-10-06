package co.edu.corhuila.opti.products.adapter.in.http;

import java.time.Instant;
import java.util.UUID;

import co.edu.corhuila.opti.products.domain.model.Liquid;
import co.edu.corhuila.opti.products.domain.model.LiquidStatus;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.ReservationStatus;

/**
 * Request and response objects of the public contract. The domain entities are never serialized
 * directly, so renaming an internal field cannot break a client.
 */
final class LiquidDtos {

    private LiquidDtos() {
    }

    record RegisterLiquidRequest(String sku, String brand, Integer volumeMl, Long costCents, Long salePriceCents,
                                 Integer stock, Integer minStock) {

        Liquid.RegisterData toData() {
            return new Liquid.RegisterData(sku, brand, volumeMl, costCents, salePriceCents, stock, minStock);
        }
    }

    record MinStockRequest(Integer minStock) {
    }

    record StockEntryRequest(Integer quantity) {
    }

    record ReserveRequest(Integer quantity, String reference) {
    }

    record LiquidResponse(UUID id, String sku, String brand, int volumeMl, long costCents, long salePriceCents,
                          int stock, int minStock, boolean lowStock, LiquidStatus status, Instant createdAt) {

        static LiquidResponse from(Liquid l) {
            return new LiquidResponse(l.id(), l.sku(), l.brand(), l.volumeMl(), l.costCents(), l.salePriceCents(),
                    l.stock(), l.minStock(), l.lowStock(), l.status(), l.createdAt());
        }
    }

    /** {@code liquidId} is the same reservation entity used for frames, read from its own table. */
    record ReservationResponse(UUID id, UUID liquidId, String sku, String description, int quantity,
                               long unitPriceCents, String reference, ReservationStatus status, Instant createdAt) {

        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.id(), r.frameId(), r.sku(), r.description(), r.quantity(),
                    r.unitPriceCents(), r.reference(), r.status(), r.createdAt());
        }
    }
}
