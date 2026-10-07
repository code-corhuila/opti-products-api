package co.edu.corhuila.opti.products.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.LensUseCases.LensFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.domain.model.Lens;

/** Persistence of lenses. */
public interface LensRepository {

    void insert(Lens lens);

    Optional<Lens> findById(UUID id);

    /** Reads the lens locking its row until the unit of work ends, so stock changes are serialized. */
    Optional<Lens> findByIdForUpdate(UUID id);

    boolean existsBySku(String sku);

    PageResult<Lens> search(LensFilter filter, PageQuery page);

    void update(Lens lens);
}
