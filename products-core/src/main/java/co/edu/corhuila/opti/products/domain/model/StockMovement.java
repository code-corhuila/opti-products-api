package co.edu.corhuila.opti.products.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One line of the stock ledger. Never updated, never deleted. */
public record StockMovement(UUID id, UUID frameId, MovementType type, int quantity, String reference, String reason,
                            Instant createdAt) {

    public static StockMovement of(UUID id, UUID frameId, MovementType type, int quantity, String reference,
                                   String reason, Instant now) {
        return new StockMovement(id, frameId, type, quantity, reference, reason, now);
    }
}
