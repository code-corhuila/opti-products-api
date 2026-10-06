package co.edu.corhuila.opti.products.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.AccessoryUseCases.AccessoryFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.domain.model.Accessory;

/** Persistence of accessories. */
public interface AccessoryRepository {

    void insert(Accessory accessory);

    Optional<Accessory> findById(UUID id);

    /** Reads the accessory locking its row until the unit of work ends, so stock changes are serialized. */
    Optional<Accessory> findByIdForUpdate(UUID id);

    boolean existsBySku(String sku);

    PageResult<Accessory> search(AccessoryFilter filter, PageQuery page);

    void update(Accessory accessory);
}
