package co.edu.corhuila.opti.products.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lens aggregate root. Pure domain object.
 * Invariants: stock never negative, sale price never below cost, money in cents (integers),
 * refractive index between 1.00 and 2.00 stored as hundredths (100-200).
 */
public final class Lens {

    private static final Pattern SKU = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,59}$");
    private static final long MAX_CENTS = 100_000_000_000L;
    private static final int MAX_STOCK = 1_000_000;
    private static final int MIN_REFRACTIVE_INDEX = 100;
    private static final int MAX_REFRACTIVE_INDEX = 200;

    private final UUID id;
    private final String sku;
    private final String brand;
    private final LensType lensType;
    private final String material;
    private final String coating;
    private final Integer refractiveIndexX100;
    private final long costCents;
    private final long salePriceCents;
    private final int stock;
    private final int minStock;
    private final LensStatus status;
    private final Instant createdAt;

    private Lens(UUID id, String sku, String brand, LensType lensType, String material, String coating,
                 Integer refractiveIndexX100, long costCents, long salePriceCents, int stock, int minStock,
                 LensStatus status, Instant createdAt) {
        this.id = id;
        this.sku = sku;
        this.brand = brand;
        this.lensType = lensType;
        this.material = material;
        this.coating = coating;
        this.refractiveIndexX100 = refractiveIndexX100;
        this.costCents = costCents;
        this.salePriceCents = salePriceCents;
        this.stock = stock;
        this.minStock = minStock;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Registers a new lens; every broken rule is reported with its field. */
    public static Lens register(UUID id, RegisterData d, Instant now) {
        Violations v = new Violations();
        String sku = v.check(() -> Validation.matching(d.sku(), "sku", SKU,
                "must have 3 to 60 letters, digits, dots, dashes or underscores"));
        String brand = v.check(() -> Validation.text(d.brand(), "brand", 2, 80));
        LensType lensType = v.check(() -> lensType(d.lensType()));
        String material = v.check(() -> Validation.optionalText(d.material(), "material", 60));
        String coating = v.check(() -> Validation.optionalText(d.coating(), "coating", 80));
        Integer refractiveIndexX100 = v.check(() -> refractiveIndex(d.refractiveIndexX100()));
        Long cost = v.check(() -> money(d.costCents(), "costCents"));
        Long price = v.check(() -> money(d.salePriceCents(), "salePriceCents"));
        Integer stock = v.check(() -> quantity(d.stock(), "stock", 0));
        Integer min = v.check(() -> quantity(d.minStock(), "minStock", 0));
        if (cost != null && price != null && price < cost) {
            v.check(() -> {
                throw DomainException.validation("salePriceCents", "must be greater than or equal to costCents");
            });
        }
        v.throwIfAny();
        return new Lens(id, sku, brand, lensType, material, coating, refractiveIndexX100, cost, price, stock, min,
                LensStatus.ACTIVE, now);
    }

    public static Lens rehydrate(UUID id, String sku, String brand, LensType lensType, String material,
                                 String coating, Integer refractiveIndexX100, long costCents, long salePriceCents,
                                 int stock, int minStock, LensStatus status, Instant createdAt) {
        return new Lens(id, sku, brand, lensType, material, coating, refractiveIndexX100, costCents, salePriceCents,
                stock, minStock, status, createdAt);
    }

    /** Puts units back, either a supplier entry or a correction. */
    public Lens restock(int quantity) {
        if (status != LensStatus.ACTIVE) {
            throw DomainException.rule("the lens is not active");
        }
        if ((long) stock + quantity > MAX_STOCK) {
            throw DomainException.rule("stock cannot exceed " + MAX_STOCK + " units");
        }
        return withStock(stock + quantity);
    }

    public Lens withMinStock(int newMin) {
        quantityOrThrow(newMin, "minStock", 0);
        return new Lens(id, sku, brand, lensType, material, coating, refractiveIndexX100, costCents, salePriceCents,
                stock, newMin, status, createdAt);
    }

    private Lens withStock(int newStock) {
        return new Lens(id, sku, brand, lensType, material, coating, refractiveIndexX100, costCents, salePriceCents,
                newStock, minStock, status, createdAt);
    }

    /** A lens at or below its minimum needs restocking. */
    public boolean lowStock() {
        return stock <= minStock;
    }

    public String description() {
        return "Lens " + brand + " " + lensType;
    }

    private static LensType lensType(String value) {
        Validation.required(value, "lensType");
        try {
            return LensType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw DomainException.validation("lensType", "must be one of MONOFOCAL, BIFOCAL, PROGRESSIVE, OCCUPATIONAL");
        }
    }

    private static Integer refractiveIndex(Integer value) {
        if (value == null) {
            return null;
        }
        return Validation.intBetween(value, "refractiveIndexX100", MIN_REFRACTIVE_INDEX, MAX_REFRACTIVE_INDEX);
    }

    /** Validates a unit quantity (units to move, minimum stock...). */
    public static int quantity(Integer value, String field, int min) {
        Validation.required(value, field);
        return quantityOrThrow(value, field, min);
    }

    private static int quantityOrThrow(int value, String field, int min) {
        return Validation.intBetween(value, field, min, MAX_STOCK);
    }

    private static long money(Long value, String field) {
        Validation.required(value, field);
        if (value < 0 || value > MAX_CENTS) {
            throw DomainException.validation(field, "must be between 0 and " + MAX_CENTS + " cents");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public String sku() {
        return sku;
    }

    public String brand() {
        return brand;
    }

    public LensType lensType() {
        return lensType;
    }

    public String material() {
        return material;
    }

    public String coating() {
        return coating;
    }

    public Integer refractiveIndexX100() {
        return refractiveIndexX100;
    }

    public long costCents() {
        return costCents;
    }

    public long salePriceCents() {
        return salePriceCents;
    }

    public int stock() {
        return stock;
    }

    public int minStock() {
        return minStock;
    }

    public LensStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Raw input to register a lens, before validation. */
    public record RegisterData(String sku, String brand, String lensType, String material, String coating,
                               Integer refractiveIndexX100, Long costCents, Long salePriceCents, Integer stock,
                               Integer minStock) {
    }
}
