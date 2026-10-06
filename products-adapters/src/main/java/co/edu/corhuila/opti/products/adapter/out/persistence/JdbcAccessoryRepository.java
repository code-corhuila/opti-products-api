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

import co.edu.corhuila.opti.products.application.port.in.AccessoryUseCases.AccessoryFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.AccessoryRepository;
import co.edu.corhuila.opti.products.domain.model.Accessory;
import co.edu.corhuila.opti.products.domain.model.AccessoryStatus;
import co.edu.corhuila.opti.products.domain.model.DomainException;

/** PostgreSQL implementation of {@link AccessoryRepository}. The schema belongs to products-db. */
public class JdbcAccessoryRepository implements AccessoryRepository {

    private static final String COLUMNS = """
            id, sku, brand, category, cost_cents, sale_price_cents, stock, min_stock, status, created_at""";

    private final JdbcClient jdbc;

    public JdbcAccessoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Accessory a) {
        try {
            jdbc.sql("INSERT INTO accessory (" + COLUMNS + ") VALUES (:id, :sku, :brand, :category, :cost, :price,"
                            + " :stock, :min, :status, :createdAt)")
                    .param("id", a.id()).param("sku", a.sku()).param("brand", a.brand())
                    .param("category", a.category()).param("cost", a.costCents()).param("price", a.salePriceCents())
                    .param("stock", a.stock()).param("min", a.minStock()).param("status", a.status().name())
                    .param("createdAt", Sql.ts(a.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw DomainException.rule("an accessory with this sku already exists");
        }
    }

    @Override
    public Optional<Accessory> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accessory WHERE id = :id")
                .param("id", id).query(JdbcAccessoryRepository::map).optional();
    }

    @Override
    public Optional<Accessory> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accessory WHERE id = :id FOR UPDATE")
                .param("id", id).query(JdbcAccessoryRepository::map).optional();
    }

    @Override
    public boolean existsBySku(String sku) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM accessory WHERE sku = :sku)")
                .param("sku", sku).query(Boolean.class).single();
    }

    @Override
    public PageResult<Accessory> search(AccessoryFilter filter, PageQuery page) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.query() != null && !filter.query().isBlank()) {
            conditions.add("(lower(sku) LIKE :q OR lower(coalesce(brand, '')) LIKE :q)");
            params.put("q", Sql.contains(filter.query()));
        }
        if (filter.status() != null) {
            conditions.add("status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.lowStock() != null) {
            conditions.add(filter.lowStock() ? "stock <= min_stock" : "stock > min_stock");
        }
        if (filter.category() != null) {
            conditions.add("category = :category");
            params.put("category", filter.category());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbc.sql("SELECT count(*) FROM accessory" + where).params(params).query(Long.class).single();
        List<Accessory> rows = jdbc.sql("SELECT " + COLUMNS + " FROM accessory" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcAccessoryRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    @Override
    public void update(Accessory a) {
        jdbc.sql("UPDATE accessory SET stock = :stock, min_stock = :min WHERE id = :id")
                .param("stock", a.stock()).param("min", a.minStock()).param("id", a.id())
                .update();
    }

    private static Accessory map(ResultSet rs, int row) throws SQLException {
        return Accessory.rehydrate(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("brand"),
                rs.getString("category"), rs.getLong("cost_cents"), rs.getLong("sale_price_cents"),
                rs.getInt("stock"), rs.getInt("min_stock"), AccessoryStatus.valueOf(rs.getString("status")),
                Sql.instant(rs, "created_at"));
    }
}
