package co.edu.corhuila.opti.products.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Liquid aggregate root (HU-25): lens cleaner, contact lens solution and the like, sold by
 * container size. Invariants: stock never negative, sale price never below cost, money in cents
 * (integers), volume in millilitres between 1 and 5000 (a reasonable bound for a retail container).
 */
public final class Liquid {

    private static final Pattern SKU = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,59}$");
    private static final long MAX_CENTS = 100_000_000_000L;
    private static final int MAX_STOCK = 1_000_000;
    private static final int MAX_VOLUME_ML = 5_000;

    private final UUID id;
    private final String sku;
    private final String brand;
    private final int volumeMl;
    private final long costCents;
    private final long salePriceCents;
    private final int stock;
    private final int minStock;
    private final LiquidStatus status;
    private final Instant createdAt;

    private Liquid(UUID id, String sku, String brand, int volumeMl, long costCents, long salePriceCents, int stock,
                   int minStock, LiquidStatus status, Instant createdAt) {
        this.id = id;
        this.sku = sku;
        this.brand = brand;
        this.volumeMl = volumeMl;
        this.costCents = costCents;
        this.salePriceCents = salePriceCents;
        this.stock = stock;
        this.minStock = minStock;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Registers a new liquid; every broken rule is reported with its field. */
    public static Liquid register(UUID id, RegisterData d, Instant now) {
        Violations v = new Violations();
        String sku = v.check(() -> Validation.matching(d.sku(), "sku", SKU,
                "must have 3 to 60 letters, digits, dots, dashes or underscores"));
        String brand = v.check(() -> Validation.text(d.brand(), "brand", 2, 80));
        Integer volume = v.check(() -> volumeMl(d.volumeMl()));
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
        return new Liquid(id, sku, brand, volume, cost, price, stock, min, LiquidStatus.ACTIVE, now);
    }

    public static Liquid rehydrate(UUID id, String sku, String brand, int volumeMl, long costCents,
                                   long salePriceCents, int stock, int minStock, LiquidStatus status,
                                   Instant createdAt) {
        return new Liquid(id, sku, brand, volumeMl, costCents, salePriceCents, stock, minStock, status, createdAt);
    }

    /** Takes units out of stock for a sale. Fails when the liquid is inactive or has not enough units. */
    public Liquid reserve(int quantity) {
        if (status != LiquidStatus.ACTIVE) {
            throw DomainException.rule("the liquid is not active");
        }
        if (stock < quantity) {
            throw DomainException.rule("insufficient stock: " + stock + " available, " + quantity + " requested");
        }
        return withStock(stock - quantity);
    }

    /** Puts units back, either a supplier entry or the return of a released reservation. */
    public Liquid restock(int quantity) {
        if ((long) stock + quantity > MAX_STOCK) {
            throw DomainException.rule("stock cannot exceed " + MAX_STOCK + " units");
        }
        return withStock(stock + quantity);
    }

    public Liquid withMinStock(int newMin) {
        quantityOrThrow(newMin, "minStock", 0);
        return new Liquid(id, sku, brand, volumeMl, costCents, salePriceCents, stock, newMin, status, createdAt);
    }

    private Liquid withStock(int newStock) {
        return new Liquid(id, sku, brand, volumeMl, costCents, salePriceCents, newStock, minStock, status, createdAt);
    }

    /** A liquid at or below its minimum needs restocking. */
    public boolean lowStock() {
        return stock <= minStock;
    }

    public String description() {
        return "Liquid " + brand + " " + volumeMl + "ml";
    }

    private static Integer volumeMl(Integer value) {
        Validation.required(value, "volumeMl");
        return Validation.intBetween(value, "volumeMl", 1, MAX_VOLUME_ML);
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

    public int volumeMl() {
        return volumeMl;
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

    public LiquidStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Raw input to register a liquid, before validation. */
    public record RegisterData(String sku, String brand, Integer volumeMl, Long costCents, Long salePriceCents,
                               Integer stock, Integer minStock) {
    }
}
