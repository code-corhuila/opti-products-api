package co.edu.corhuila.opti.products.testsupport;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.FrameUseCases.FrameFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.FrameRepository;
import co.edu.corhuila.opti.products.domain.model.Frame;

/** Fake of the frame store, used to test the core and the HTTP adapter without a database. */
public class InMemoryFrameRepository implements FrameRepository {

    private final Map<UUID, Frame> byId = new LinkedHashMap<>();

    @Override
    public void insert(Frame frame) {
        byId.put(frame.id(), frame);
    }

    @Override
    public Optional<Frame> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Frame> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public boolean existsBySku(String sku) {
        return byId.values().stream().anyMatch(f -> f.sku().equals(sku));
    }

    @Override
    public PageResult<Frame> search(FrameFilter filter, PageQuery page) {
        List<Frame> matches = byId.values().stream()
                .filter(f -> filter.status() == null || f.status() == filter.status())
                .filter(f -> filter.lowStock() == null || f.lowStock() == filter.lowStock())
                .filter(f -> filter.query() == null || filter.query().isBlank() || matches(f, filter.query()))
                .sorted(Comparator.comparing(Frame::createdAt).reversed().thenComparing(Frame::id))
                .toList();
        int from = Math.min(page.offset(), matches.size());
        int to = Math.min(from + page.limit(), matches.size());
        return new PageResult<>(matches.subList(from, to), page.page(), page.limit(), matches.size());
    }

    @Override
    public void update(Frame frame) {
        byId.put(frame.id(), frame);
    }

    private static boolean matches(Frame f, String query) {
        String q = query.trim().toLowerCase();
        return f.sku().toLowerCase().contains(q) || (f.brand() + " " + f.model()).toLowerCase().contains(q);
    }
}
