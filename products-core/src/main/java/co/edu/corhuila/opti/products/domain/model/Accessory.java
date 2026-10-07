package co.edu.corhuila.opti.products.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Accessory aggregate root (HU-25): cases, cleaning cloths, straps and the like. {@code category}
 * is free text (not a closed enum) on purpose: the catalogue of accessory kinds keeps growing and
 * is store-defined, unlike lens type or frame gender which are fixed by the business.
 * Invariants: stock never negative, sale price never below cost, money in cents (integers).
 */
public final class Accessory {

    private static final Pattern SKU = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,59}$");
    private static final long MAX_CENTS = 100_000_000_000L;
    private static final int MAX_STOCK = 1_000_000;

    private final UUID id;
    private final String sku;
    private final String brand;
    private final String category;
    private final long costCents;
    private final long salePriceCents;
    private final int stock;
    private final int minStock;
    private final AccessoryStatus status;
    private final Instant createdAt;

    private Accessory(UUID id, String sku, String brand, String category, long costCents, long salePriceCents,
                      int stock, int minStock, AccessoryStatus status, Instant createdAt) {
        this.id = id;
        this.sku = sku;
        this.brand = brand;
        this.category = category;
        this.costCents = costCents;
        this.salePriceCents = salePriceCents;
        this.stock = stock;
        this.minStock = minStock;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Registers a new accessory; every broken rule is reported with its field. */
    public static Accessory register(UUID id, RegisterData d, Instant now) {
        Violations v = new Violations();
        String sku = v.check(() -> Validation.matching(d.sku(), "sku", SKU,
                "must have 3 to 60 letters, digits, dots, dashes or underscores"));
        String brand = v.check(() -> Validation.optionalText(d.brand(), "brand", 80));
        String category = v.check(() -> Validation.text(d.category(), "category", 2, 60));
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
        return new Accessory(id, sku, brand, category, cost, price, stock, min, AccessoryStatus.ACTIVE, now);
    }

    public static Accessory rehydrate(UUID id, String sku, String brand, String category, long costCents,
                                      long salePriceCents, int stock, int minStock, AccessoryStatus status,
                                      Instant createdAt) {
        return new Accessory(id, sku, brand, category, costCents, salePriceCents, stock, minStock, status, createdAt);
    }

    /** Takes units out of stock for a sale. Fails when the accessory is inactive or has not enough units. */
    public Accessory reserve(int quantity) {
        if (status != AccessoryStatus.ACTIVE) {
            throw DomainException.rule("the accessory is not active");
        }
        if (stock < quantity) {
            throw DomainException.rule("insufficient stock: " + stock + " available, " + quantity + " requested");
        }
        return withStock(stock - quantity);
    }

    /** Puts units back, either a supplier entry or the return of a released reservation. */
    public Accessory restock(int quantity) {
        if ((long) stock + quantity > MAX_STOCK) {
            throw DomainException.rule("stock cannot exceed " + MAX_STOCK + " units");
        }
        return withStock(stock + quantity);
    }

    public Accessory withMinStock(int newMin) {
        quantityOrThrow(newMin, "minStock", 0);
        return new Accessory(id, sku, brand, category, costCents, salePriceCents, stock, newMin, status, createdAt);
    }

    private Accessory withStock(int newStock) {
        return new Accessory(id, sku, brand, category, costCents, salePriceCents, newStock, minStock, status, createdAt);
    }

    /** An accessory at or below its minimum needs restocking. */
    public boolean lowStock() {
        return stock <= minStock;
    }

    public String description() {
        return "Accessory " + category + (brand != null ? " " + brand : "");
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

    public String category() {
        return category;
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

    public AccessoryStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Raw input to register an accessory, before validation. */
    public record RegisterData(String sku, String brand, String category, Long costCents, Long salePriceCents,
                               Integer stock, Integer minStock) {
    }
}
