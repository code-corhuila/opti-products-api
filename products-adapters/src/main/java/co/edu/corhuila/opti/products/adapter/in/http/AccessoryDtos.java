package co.edu.corhuila.opti.products.adapter.in.http;

import java.time.Instant;
import java.util.UUID;

import co.edu.corhuila.opti.products.domain.model.Accessory;
import co.edu.corhuila.opti.products.domain.model.AccessoryStatus;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.ReservationStatus;

/**
 * Request and response objects of the public contract. The domain entities are never serialized
 * directly, so renaming an internal field cannot break a client.
 */
final class AccessoryDtos {

    private AccessoryDtos() {
    }

    record RegisterAccessoryRequest(String sku, String brand, String category, Long costCents, Long salePriceCents,
                                    Integer stock, Integer minStock) {

        Accessory.RegisterData toData() {
            return new Accessory.RegisterData(sku, brand, category, costCents, salePriceCents, stock, minStock);
        }
    }

    record MinStockRequest(Integer minStock) {
    }

    record StockEntryRequest(Integer quantity) {
    }

    record ReserveRequest(Integer quantity, String reference) {
    }

    record AccessoryResponse(UUID id, String sku, String brand, String category, long costCents, long salePriceCents,
                             int stock, int minStock, boolean lowStock, AccessoryStatus status, Instant createdAt) {

        static AccessoryResponse from(Accessory a) {
            return new AccessoryResponse(a.id(), a.sku(), a.brand(), a.category(), a.costCents(), a.salePriceCents(),
                    a.stock(), a.minStock(), a.lowStock(), a.status(), a.createdAt());
        }
    }

    /** {@code accessoryId} is the same reservation entity used for frames, read from its own table. */
    record ReservationResponse(UUID id, UUID accessoryId, String sku, String description, int quantity,
                               long unitPriceCents, String reference, ReservationStatus status, Instant createdAt) {

        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.id(), r.frameId(), r.sku(), r.description(), r.quantity(),
                    r.unitPriceCents(), r.reference(), r.status(), r.createdAt());
        }
    }
}
