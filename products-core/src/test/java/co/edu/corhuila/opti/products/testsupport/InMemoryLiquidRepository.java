package co.edu.corhuila.opti.products.testsupport;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases.LiquidFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.LiquidRepository;
import co.edu.corhuila.opti.products.domain.model.Liquid;

/** Fake of the liquid store, used to test the core and the HTTP adapter without a database. */
public class InMemoryLiquidRepository implements LiquidRepository {

    private final Map<UUID, Liquid> byId = new LinkedHashMap<>();

    @Override
    public void insert(Liquid liquid) {
        byId.put(liquid.id(), liquid);
    }

    @Override
    public Optional<Liquid> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Liquid> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public boolean existsBySku(String sku) {
        return byId.values().stream().anyMatch(l -> l.sku().equals(sku));
    }

    @Override
    public PageResult<Liquid> search(LiquidFilter filter, PageQuery page) {
        List<Liquid> matches = byId.values().stream()
                .filter(l -> filter.status() == null || l.status() == filter.status())
                .filter(l -> filter.lowStock() == null || l.lowStock() == filter.lowStock())
                .filter(l -> filter.query() == null || filter.query().isBlank() || matches(l, filter.query()))
                .sorted(Comparator.comparing(Liquid::createdAt).reversed().thenComparing(Liquid::id))
                .toList();
        int from = Math.min(page.offset(), matches.size());
        int to = Math.min(from + page.limit(), matches.size());
        return new PageResult<>(matches.subList(from, to), page.page(), page.limit(), matches.size());
    }

    @Override
    public void update(Liquid liquid) {
        byId.put(liquid.id(), liquid);
    }

    private static boolean matches(Liquid l, String query) {
        String q = query.trim().toLowerCase();
        return l.sku().toLowerCase().contains(q) || l.brand().toLowerCase().contains(q);
    }
}
