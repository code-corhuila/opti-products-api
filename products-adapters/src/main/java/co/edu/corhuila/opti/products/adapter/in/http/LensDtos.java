package co.edu.corhuila.opti.products.adapter.in.http;

import java.time.Instant;
import java.util.UUID;

import co.edu.corhuila.opti.products.domain.model.Lens;
import co.edu.corhuila.opti.products.domain.model.LensStatus;
import co.edu.corhuila.opti.products.domain.model.LensType;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.ReservationStatus;

/**
 * Request and response objects of the public contract. The domain entities are never serialized
 * directly, so renaming an internal field cannot break a client.
 */
final class LensDtos {

    private LensDtos() {
    }

    record RegisterLensRequest(String sku, String brand, String lensType, String material, String coating,
                               Integer refractiveIndexX100, Long costCents, Long salePriceCents, Integer stock,
                               Integer minStock) {

        Lens.RegisterData toData() {
            return new Lens.RegisterData(sku, brand, lensType, material, coating, refractiveIndexX100, costCents,
                    salePriceCents, stock, minStock);
        }
    }

    record MinStockRequest(Integer minStock) {
    }

    record StockEntryRequest(Integer quantity) {
    }

    record ReserveRequest(Integer quantity, String reference) {
    }

    record LensResponse(UUID id, String sku, String brand, LensType lensType, String material, String coating,
                        Integer refractiveIndexX100, long costCents, long salePriceCents, int stock, int minStock,
                        boolean lowStock, LensStatus status, Instant createdAt) {

        static LensResponse from(Lens l) {
            return new LensResponse(l.id(), l.sku(), l.brand(), l.lensType(), l.material(), l.coating(),
                    l.refractiveIndexX100(), l.costCents(), l.salePriceCents(), l.stock(), l.minStock(),
                    l.lowStock(), l.status(), l.createdAt());
        }
    }

    /** {@code lensId} is the same reservation entity used for frames, read from its own table. */
    record ReservationResponse(UUID id, UUID lensId, String sku, String description, int quantity,
                               long unitPriceCents, String reference, ReservationStatus status, Instant createdAt) {

        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.id(), r.frameId(), r.sku(), r.description(), r.quantity(),
                    r.unitPriceCents(), r.reference(), r.status(), r.createdAt());
        }
    }
}
