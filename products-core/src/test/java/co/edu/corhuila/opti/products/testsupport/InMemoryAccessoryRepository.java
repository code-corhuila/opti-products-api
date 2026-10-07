package co.edu.corhuila.opti.products.testsupport;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.AccessoryUseCases.AccessoryFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.AccessoryRepository;
import co.edu.corhuila.opti.products.domain.model.Accessory;

/** Fake of the accessory store, used to test the core and the HTTP adapter without a database. */
public class InMemoryAccessoryRepository implements AccessoryRepository {

    private final Map<UUID, Accessory> byId = new LinkedHashMap<>();

    @Override
    public void insert(Accessory accessory) {
        byId.put(accessory.id(), accessory);
    }

    @Override
    public Optional<Accessory> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Accessory> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public boolean existsBySku(String sku) {
        return byId.values().stream().anyMatch(a -> a.sku().equals(sku));
    }

    @Override
    public PageResult<Accessory> search(AccessoryFilter filter, PageQuery page) {
        List<Accessory> matches = byId.values().stream()
                .filter(a -> filter.status() == null || a.status() == filter.status())
                .filter(a -> filter.lowStock() == null || a.lowStock() == filter.lowStock())
                .filter(a -> filter.category() == null || filter.category().equals(a.category()))
                .filter(a -> filter.query() == null || filter.query().isBlank() || matches(a, filter.query()))
                .sorted(Comparator.comparing(Accessory::createdAt).reversed().thenComparing(Accessory::id))
                .toList();
        int from = Math.min(page.offset(), matches.size());
        int to = Math.min(from + page.limit(), matches.size());
        return new PageResult<>(matches.subList(from, to), page.page(), page.limit(), matches.size());
    }

    @Override
    public void update(Accessory accessory) {
        byId.put(accessory.id(), accessory);
    }

    private static boolean matches(Accessory a, String query) {
        String q = query.trim().toLowerCase();
        return a.sku().toLowerCase().contains(q) || (a.brand() != null && a.brand().toLowerCase().contains(q));
    }
}
