package co.edu.corhuila.opti.products.testsupport;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.LensUseCases.LensFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.LensRepository;
import co.edu.corhuila.opti.products.domain.model.Lens;

/** Fake of the lens store, used to test the core and the HTTP adapter without a database. */
public class InMemoryLensRepository implements LensRepository {

    private final Map<UUID, Lens> byId = new LinkedHashMap<>();

    @Override
    public void insert(Lens lens) {
        byId.put(lens.id(), lens);
    }

    @Override
    public Optional<Lens> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Lens> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public boolean existsBySku(String sku) {
        return byId.values().stream().anyMatch(l -> l.sku().equals(sku));
    }

    @Override
    public PageResult<Lens> search(LensFilter filter, PageQuery page) {
        List<Lens> matches = byId.values().stream()
                .filter(l -> filter.status() == null || l.status() == filter.status())
                .filter(l -> filter.lowStock() == null || l.lowStock() == filter.lowStock())
                .filter(l -> filter.query() == null || filter.query().isBlank() || matches(l, filter.query()))
                .sorted(Comparator.comparing(Lens::createdAt).reversed().thenComparing(Lens::id))
                .toList();
        int from = Math.min(page.offset(), matches.size());
        int to = Math.min(from + page.limit(), matches.size());
        return new PageResult<>(matches.subList(from, to), page.page(), page.limit(), matches.size());
    }

    @Override
    public void update(Lens lens) {
        byId.put(lens.id(), lens);
    }

    private static boolean matches(Lens l, String query) {
        String q = query.trim().toLowerCase();
        return l.sku().toLowerCase().contains(q) || l.brand().toLowerCase().contains(q);
    }
}
