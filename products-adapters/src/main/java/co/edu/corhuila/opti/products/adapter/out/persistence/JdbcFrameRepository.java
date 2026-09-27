package co.edu.corhuila.opti.products.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.products.application.port.in.FrameUseCases.FrameFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.FrameRepository;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Frame;
import co.edu.corhuila.opti.products.domain.model.FrameStatus;

/** PostgreSQL implementation of {@link FrameRepository}. The schema belongs to products-db. */
public class JdbcFrameRepository implements FrameRepository {

    private static final String COLUMNS = """
            id, sku, brand, model, color, material, gender, cost_cents, sale_price_cents, stock, min_stock,
            location, supplier, status, created_at""";

    private final JdbcClient jdbc;

    public JdbcFrameRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Frame f) {
        try {
            jdbc.sql("INSERT INTO frame (" + COLUMNS + ") VALUES (:id, :sku, :brand, :model, :color, :material,"
                            + " :gender, :cost, :price, :stock, :min, :location, :supplier, :status, :createdAt)")
                    .param("id", f.id()).param("sku", f.sku()).param("brand", f.brand()).param("model", f.model())
                    .param("color", f.color()).param("material", f.material()).param("gender", f.gender())
                    .param("cost", f.costCents()).param("price", f.salePriceCents()).param("stock", f.stock())
                    .param("min", f.minStock()).param("location", f.location()).param("supplier", f.supplier())
                    .param("status", f.status().name()).param("createdAt", Sql.ts(f.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw DomainException.rule("a frame with this sku already exists");
        }
    }

    @Override
    public Optional<Frame> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM frame WHERE id = :id")
                .param("id", id).query(JdbcFrameRepository::map).optional();
    }

    @Override
    public Optional<Frame> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM frame WHERE id = :id FOR UPDATE")
                .param("id", id).query(JdbcFrameRepository::map).optional();
    }

    @Override
    public boolean existsBySku(String sku) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM frame WHERE sku = :sku)")
                .param("sku", sku).query(Boolean.class).single();
    }

    @Override
    public PageResult<Frame> search(FrameFilter filter, PageQuery page) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.query() != null && !filter.query().isBlank()) {
            conditions.add("(lower(sku) LIKE :q OR lower(brand || ' ' || model) LIKE :q)");
            params.put("q", Sql.contains(filter.query()));
        }
        if (filter.status() != null) {
            conditions.add("status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.lowStock() != null) {
            conditions.add(filter.lowStock() ? "stock <= min_stock" : "stock > min_stock");
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbc.sql("SELECT count(*) FROM frame" + where).params(params).query(Long.class).single();
        List<Frame> rows = jdbc.sql("SELECT " + COLUMNS + " FROM frame" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcFrameRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    @Override
    public void update(Frame f) {
        jdbc.sql("UPDATE frame SET stock = :stock, min_stock = :min WHERE id = :id")
                .param("stock", f.stock()).param("min", f.minStock()).param("id", f.id())
                .update();
    }

    private static Frame map(ResultSet rs, int row) throws SQLException {
        return Frame.rehydrate(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("brand"),
                rs.getString("model"), rs.getString("color"), rs.getString("material"), rs.getString("gender"),
                rs.getLong("cost_cents"), rs.getLong("sale_price_cents"), rs.getInt("stock"), rs.getInt("min_stock"),
                rs.getString("location"), rs.getString("supplier"), FrameStatus.valueOf(rs.getString("status")),
                Sql.instant(rs, "created_at"));
    }
}
