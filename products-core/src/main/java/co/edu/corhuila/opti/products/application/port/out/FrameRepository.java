package co.edu.corhuila.opti.products.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.in.FrameUseCases.FrameFilter;
import co.edu.corhuila.opti.products.application.port.in.FrameUseCases.FrameSummary;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.domain.model.Frame;

/** Persistence of frames. */
public interface FrameRepository {

    void insert(Frame frame);

    Optional<Frame> findById(UUID id);

    /** Reads the frame locking its row until the unit of work ends, so stock changes are serialized. */
    Optional<Frame> findByIdForUpdate(UUID id);

    boolean existsBySku(String sku);

    PageResult<Frame> search(FrameFilter filter, PageQuery page);

    void update(Frame frame);

    /** Aggregate counters computed in the database for the inventory dashboard. */
    FrameSummary summary();

    /** Distinct brand names currently in the catalogue, alphabetically sorted. */
    List<String> brands();
}
