package co.edu.corhuila.opti.products.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.StockMovementRepository;
import co.edu.corhuila.opti.products.domain.model.MovementType;
import co.edu.corhuila.opti.products.domain.model.StockMovement;

/** PostgreSQL implementation of the append-only stock ledger. */
public class JdbcStockMovementRepository implements StockMovementRepository {

    private static final String COLUMNS = "id, frame_id, movement_type, quantity, reference, reason, created_at";

    private final JdbcClient jdbc;

    public JdbcStockMovementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(StockMovement m) {
        jdbc.sql("INSERT INTO stock_movement (" + COLUMNS + ") VALUES (:id, :frame, :type, :quantity, :reference,"
                        + " :reason, :createdAt)")
                .param("id", m.id()).param("frame", m.frameId()).param("type", m.type().name())
                .param("quantity", m.quantity()).param("reference", m.reference()).param("reason", m.reason())
                .param("createdAt", Sql.ts(m.createdAt()))
                .update();
    }

    @Override
    public Optional<StockMovement> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM stock_movement WHERE id = :id")
                .param("id", id).query(JdbcStockMovementRepository::map).optional();
    }

    @Override
    public PageResult<StockMovement> list(UUID frameId, PageQuery page) {
        long total = jdbc.sql("SELECT count(*) FROM stock_movement WHERE frame_id = :frame")
                .param("frame", frameId).query(Long.class).single();
        List<StockMovement> rows = jdbc.sql("SELECT " + COLUMNS + " FROM stock_movement WHERE frame_id = :frame"
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("frame", frameId).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcStockMovementRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    private static StockMovement map(ResultSet rs, int row) throws SQLException {
        return new StockMovement(rs.getObject("id", UUID.class), rs.getObject("frame_id", UUID.class),
                MovementType.valueOf(rs.getString("movement_type")), rs.getInt("quantity"),
                rs.getString("reference"), rs.getString("reason"), Sql.instant(rs, "created_at"));
    }
}
