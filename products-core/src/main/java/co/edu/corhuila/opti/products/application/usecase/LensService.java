package co.edu.corhuila.opti.products.application.usecase;

import java.time.Clock;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.LensUseCases;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.Created;
import co.edu.corhuila.opti.products.application.port.out.IdGenerator;
import co.edu.corhuila.opti.products.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.products.application.port.out.LensRepository;
import co.edu.corhuila.opti.products.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Lens;
import co.edu.corhuila.opti.products.domain.model.Validation;
import co.edu.corhuila.opti.products.domain.model.Violations;

/**
 * Lens catalogue and stock. Every creation claims its idempotency key first, inside the unit of
 * work, so a retry (even a concurrent one) changes nothing a second time.
 */
public class LensService implements LensUseCases {

    private static final String LENS = "LENS";
    private static final String LENS_STOCK_ENTRY = "LENS_STOCK_ENTRY";

    private final LensRepository lenses;
    private final IdempotencyStore keys;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public LensService(LensRepository lenses, IdempotencyStore keys, IdGenerator ids, UnitOfWork unitOfWork,
                       Clock clock) {
        this.lenses = lenses;
        this.keys = keys;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Created<Lens> register(Lens.RegisterData data, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Lens lens = v.check(() -> Lens.register(ids.next(), data, clock.instant()));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, LENS, lens.id())) {
                return new Created<>(get(boundTo(key, LENS)), false);
            }
            if (lenses.existsBySku(lens.sku())) {
                throw DomainException.rule("a lens with this sku already exists");
            }
            lenses.insert(lens);
            return new Created<>(lens, true);
        });
    }

    @Override
    public Lens get(UUID id) {
        return lenses.findById(id).orElseThrow(() -> DomainException.notFound("lens not found"));
    }

    @Override
    public PageResult<Lens> search(LensFilter filter, PageQuery page) {
        return lenses.search(filter, page);
    }

    @Override
    public Lens updateMinStock(UUID id, Integer minStock) {
        Violations v = new Violations();
        Integer min = v.check(() -> Lens.quantity(minStock, "minStock", 0));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            Lens updated = lockedLens(id).withMinStock(min);
            lenses.update(updated);
            return updated;
        });
    }

    @Override
    public Created<Lens> addStock(UUID lensId, Integer quantity, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Integer units = v.check(() -> Lens.quantity(quantity, "quantity", 1));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, LENS_STOCK_ENTRY, lensId)) {
                return new Created<>(get(lensId), false);
            }
            Lens updated = lockedLens(lensId).restock(units);
            lenses.update(updated);
            return new Created<>(updated, true);
        });
    }

    private Lens lockedLens(UUID id) {
        return lenses.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("lens not found"));
    }

    private UUID boundTo(String key, String resourceType) {
        return keys.find(key, resourceType).orElseThrow();
    }
}
