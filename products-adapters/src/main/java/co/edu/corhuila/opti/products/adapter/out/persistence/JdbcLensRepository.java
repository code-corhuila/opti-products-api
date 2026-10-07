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

import co.edu.corhuila.opti.products.application.port.in.LensUseCases.LensFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.port.in.PageResult;
import co.edu.corhuila.opti.products.application.port.out.LensRepository;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Lens;
import co.edu.corhuila.opti.products.domain.model.LensStatus;
import co.edu.corhuila.opti.products.domain.model.LensType;

/** PostgreSQL implementation of {@link LensRepository}. The schema belongs to products-db. */
public class JdbcLensRepository implements LensRepository {

    private static final String COLUMNS = """
            id, sku, brand, lens_type, material, coating, refractive_index_x100, cost_cents, sale_price_cents,
            stock, min_stock, status, created_at""";

    private final JdbcClient jdbc;

    public JdbcLensRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Lens l) {
        try {
            jdbc.sql("INSERT INTO lens (" + COLUMNS + ") VALUES (:id, :sku, :brand, :lensType, :material, :coating,"
                            + " :refractiveIndex, :cost, :price, :stock, :min, :status, :createdAt)")
                    .param("id", l.id()).param("sku", l.sku()).param("brand", l.brand())
                    .param("lensType", l.lensType().name()).param("material", l.material())
                    .param("coating", l.coating()).param("refractiveIndex", l.refractiveIndexX100())
                    .param("cost", l.costCents()).param("price", l.salePriceCents()).param("stock", l.stock())
                    .param("min", l.minStock()).param("status", l.status().name())
                    .param("createdAt", Sql.ts(l.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw DomainException.rule("a lens with this sku already exists");
        }
    }

    @Override
    public Optional<Lens> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM lens WHERE id = :id")
                .param("id", id).query(JdbcLensRepository::map).optional();
    }

    @Override
    public Optional<Lens> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM lens WHERE id = :id FOR UPDATE")
                .param("id", id).query(JdbcLensRepository::map).optional();
    }

    @Override
    public boolean existsBySku(String sku) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM lens WHERE sku = :sku)")
                .param("sku", sku).query(Boolean.class).single();
    }

    @Override
    public PageResult<Lens> search(LensFilter filter, PageQuery page) {
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

        long total = jdbc.sql("SELECT count(*) FROM lens" + where).params(params).query(Long.class).single();
        List<Lens> rows = jdbc.sql("SELECT " + COLUMNS + " FROM lens" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcLensRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    @Override
    public void update(Lens l) {
        jdbc.sql("UPDATE lens SET stock = :stock, min_stock = :min WHERE id = :id")
                .param("stock", l.stock()).param("min", l.minStock()).param("id", l.id())
                .update();
    }

    private static Lens map(ResultSet rs, int row) throws SQLException {
        return Lens.rehydrate(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("brand"),
                LensType.valueOf(rs.getString("lens_type")), rs.getString("material"), rs.getString("coating"),
                Sql.integer(rs, "refractive_index_x100"), rs.getLong("cost_cents"), rs.getLong("sale_price_cents"),
                rs.getInt("stock"), rs.getInt("min_stock"), LensStatus.valueOf(rs.getString("status")),
                Sql.instant(rs, "created_at"));
    }
}
