package co.edu.corhuila.opti.products.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases.LiquidFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.domain.model.Liquid;

/** Persistence of liquids. */
public interface LiquidRepository {

    void insert(Liquid liquid);

    Optional<Liquid> findById(UUID id);

    /** Reads the liquid locking its row until the unit of work ends, so stock changes are serialized. */
    Optional<Liquid> findByIdForUpdate(UUID id);

    boolean existsBySku(String sku);

    PageResult<Liquid> search(LiquidFilter filter, PageQuery page);

    void update(Liquid liquid);
}
