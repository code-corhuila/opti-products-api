package co.edu.corhuila.opti.products.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.products.application.port.out.ReservationRepository;
import co.edu.corhuila.opti.products.domain.model.Reservation;
import co.edu.corhuila.opti.products.domain.model.ReservationStatus;

/**
 * PostgreSQL implementation of {@link ReservationRepository} for liquids. Reuses the same
 * product-agnostic {@link Reservation} domain class and port as frames, lenses and accessories,
 * but reads and writes its own {@code liquid_reservation} table.
 */
public class JdbcLiquidReservationRepository implements ReservationRepository {

    private static final String COLUMNS = """
            id, liquid_id, sku, description, quantity, unit_price_cents, reference, status, created_at""";

    private final JdbcClient jdbc;

    public JdbcLiquidReservationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Reservation r) {
        jdbc.sql("INSERT INTO liquid_reservation (" + COLUMNS + ") VALUES (:id, :liquid, :sku, :description,"
                        + " :quantity, :price, :reference, :status, :createdAt)")
                .param("id", r.id()).param("liquid", r.frameId()).param("sku", r.sku())
                .param("description", r.description()).param("quantity", r.quantity())
                .param("price", r.unitPriceCents()).param("reference", r.reference())
                .param("status", r.status().name()).param("createdAt", Sql.ts(r.createdAt()))
                .update();
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM liquid_reservation WHERE id = :id")
                .param("id", id).query(JdbcLiquidReservationRepository::map).optional();
    }

    @Override
    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM liquid_reservation WHERE id = :id FOR UPDATE")
                .param("id", id).query(JdbcLiquidReservationRepository::map).optional();
    }

    @Override
    public void update(Reservation r) {
        jdbc.sql("UPDATE liquid_reservation SET status = :status WHERE id = :id")
                .param("status", r.status().name()).param("id", r.id()).update();
    }

    private static Reservation map(ResultSet rs, int row) throws SQLException {
        return Reservation.rehydrate(rs.getObject("id", UUID.class), rs.getObject("liquid_id", UUID.class),
                rs.getString("sku"), rs.getString("description"), rs.getInt("quantity"),
                rs.getLong("unit_price_cents"), rs.getString("reference"),
                ReservationStatus.valueOf(rs.getString("status")), Sql.instant(rs, "created_at"));
    }
}
