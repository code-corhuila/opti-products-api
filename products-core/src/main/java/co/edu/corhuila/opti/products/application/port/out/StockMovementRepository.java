package co.edu.corhuila.opti.products.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.domain.model.StockMovement;

/** The append-only stock ledger. */
public interface StockMovementRepository {

    void append(StockMovement movement);

    Optional<StockMovement> findById(UUID id);

    PageResult<StockMovement> list(UUID frameId, PageQuery page);
}
