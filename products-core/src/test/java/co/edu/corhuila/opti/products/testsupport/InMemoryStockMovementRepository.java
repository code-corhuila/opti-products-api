package co.edu.corhuila.opti.products.testsupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.StockMovementRepository;
import co.edu.corhuila.opti.products.domain.model.StockMovement;

/** Fake of the stock ledger. */
public class InMemoryStockMovementRepository implements StockMovementRepository {

    private final List<StockMovement> all = new ArrayList<>();

    @Override
    public void append(StockMovement movement) {
        all.add(movement);
    }

    @Override
    public Optional<StockMovement> findById(UUID id) {
        return all.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    @Override
    public PageResult<StockMovement> list(UUID frameId, PageQuery page) {
        List<StockMovement> mine = all.stream().filter(m -> m.frameId().equals(frameId))
                .sorted(Comparator.comparing(StockMovement::createdAt).reversed()).toList();
        int from = Math.min(page.offset(), mine.size());
        int to = Math.min(from + page.limit(), mine.size());
        return new PageResult<>(mine.subList(from, to), page.page(), page.limit(), mine.size());
    }
}
