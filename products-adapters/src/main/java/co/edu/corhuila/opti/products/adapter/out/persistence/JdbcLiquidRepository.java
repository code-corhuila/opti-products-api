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

import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases.LiquidFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.LiquidRepository;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Liquid;
import co.edu.corhuila.opti.products.domain.model.LiquidStatus;

/** PostgreSQL implementation of {@link LiquidRepository}. The schema belongs to products-db. */
public class JdbcLiquidRepository implements LiquidRepository {

    private static final String COLUMNS = """
            id, sku, brand, volume_ml, cost_cents, sale_price_cents, stock, min_stock, status, created_at""";

    private final JdbcClient jdbc;

    public JdbcLiquidRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Liquid l) {
        try {
            jdbc.sql("INSERT INTO liquid (" + COLUMNS + ") VALUES (:id, :sku, :brand, :volume, :cost, :price,"
                            + " :stock, :min, :status, :createdAt)")
                    .param("id", l.id()).param("sku", l.sku()).param("brand", l.brand())
                    .param("volume", l.volumeMl()).param("cost", l.costCents()).param("price", l.salePriceCents())
                    .param("stock", l.stock()).param("min", l.minStock()).param("status", l.status().name())
                    .param("createdAt", Sql.ts(l.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw DomainException.rule("a liquid with this sku already exists");
        }
    }

    @Override
    public Optional<Liquid> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM liquid WHERE id = :id")
                .param("id", id).query(JdbcLiquidRepository::map).optional();
    }

    @Override
    public Optional<Liquid> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM liquid WHERE id = :id FOR UPDATE")
                .param("id", id).query(JdbcLiquidRepository::map).optional();
    }

    @Override
    public boolean existsBySku(String sku) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM liquid WHERE sku = :sku)")
                .param("sku", sku).query(Boolean.class).single();
    }

    @Override
    public PageResult<Liquid> search(LiquidFilter filter, PageQuery page) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.query() != null && !filter.query().isBlank()) {
            conditions.add("(lower(sku) LIKE :q OR lower(brand) LIKE :q)");
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

        long total = jdbc.sql("SELECT count(*) FROM liquid" + where).params(params).query(Long.class).single();
        List<Liquid> rows = jdbc.sql("SELECT " + COLUMNS + " FROM liquid" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcLiquidRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    @Override
    public void update(Liquid l) {
        jdbc.sql("UPDATE liquid SET stock = :stock, min_stock = :min WHERE id = :id")
                .param("stock", l.stock()).param("min", l.minStock()).param("id", l.id())
                .update();
    }

    private static Liquid map(ResultSet rs, int row) throws SQLException {
        return Liquid.rehydrate(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("brand"),
                rs.getInt("volume_ml"), rs.getLong("cost_cents"), rs.getLong("sale_price_cents"),
                rs.getInt("stock"), rs.getInt("min_stock"), LiquidStatus.valueOf(rs.getString("status")),
                Sql.instant(rs, "created_at"));
    }
}
