package co.edu.corhuila.opti.products.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Frame aggregate root. Pure domain object.
 * Invariants: stock never negative, sale price never below cost, money in cents (integers).
 */
public final class Frame {

    private static final Pattern SKU = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,59}$");
    private static final long MAX_CENTS = 100_000_000_000L;
    private static final int MAX_STOCK = 1_000_000;

    private final UUID id;
    private final String sku;
    private final String brand;
    private final String model;
    private final String color;
    private final String material;
    private final String gender;
    private final long costCents;
    private final long salePriceCents;
    private final int stock;
    private final int minStock;
    private final String location;
    private final String supplier;
    private final FrameStatus status;
    private final Instant createdAt;
    private final String imageUrl;

    private Frame(UUID id, String sku, String brand, String model, String color, String material, String gender,
                  long costCents, long salePriceCents, int stock, int minStock, String location, String supplier,
                  FrameStatus status, Instant createdAt, String imageUrl) {
        this.id = id;
        this.sku = sku;
        this.brand = brand;
        this.model = model;
        this.color = color;
        this.material = material;
        this.gender = gender;
        this.costCents = costCents;
        this.salePriceCents = salePriceCents;
        this.stock = stock;
        this.minStock = minStock;
        this.location = location;
        this.supplier = supplier;
        this.status = status;
        this.createdAt = createdAt;
        this.imageUrl = imageUrl;
    }

    /** Registers a new frame; every broken rule is reported with its field. */
    public static Frame register(UUID id, RegisterData d, Instant now) {
        Violations v = new Violations();
        String sku = v.check(() -> Validation.matching(d.sku(), "sku", SKU,
                "must have 3 to 60 letters, digits, dots, dashes or underscores"));
        String brand = v.check(() -> Validation.text(d.brand(), "brand", 2, 80));
        String model = v.check(() -> Validation.text(d.model(), "model", 1, 80));
        String color = v.check(() -> Validation.optionalText(d.color(), "color", 60));
        String material = v.check(() -> Validation.optionalText(d.material(), "material", 60));
        String gender = v.check(() -> Validation.optionalText(d.gender(), "gender", 30));
        Long cost = v.check(() -> money(d.costCents(), "costCents"));
        Long price = v.check(() -> money(d.salePriceCents(), "salePriceCents"));
        Integer stock = v.check(() -> quantity(d.stock(), "stock", 0));
        Integer min = v.check(() -> quantity(d.minStock(), "minStock", 0));
        String location = v.check(() -> Validation.optionalText(d.location(), "location", 80));
        String supplier = v.check(() -> Validation.optionalText(d.supplier(), "supplier", 120));
        if (cost != null && price != null && price < cost) {
            v.check(() -> {
                throw DomainException.validation("salePriceCents", "must be greater than or equal to costCents");
            });
        }
        v.throwIfAny();
        return new Frame(id, sku, brand, model, color, material, gender, cost, price, stock, min, location, supplier,
                FrameStatus.ACTIVE, now, null);
    }

    public static Frame rehydrate(UUID id, String sku, String brand, String model, String color, String material,
                                  String gender, long costCents, long salePriceCents, int stock, int minStock,
                                  String location, String supplier, FrameStatus status, Instant createdAt,
                                  String imageUrl) {
        return new Frame(id, sku, brand, model, color, material, gender, costCents, salePriceCents, stock, minStock,
                location, supplier, status, createdAt, imageUrl);
    }

    /** Takes units out of stock for a sale. Fails when the frame is inactive or has not enough units. */
    public Frame reserve(int quantity) {
        if (status != FrameStatus.ACTIVE) {
            throw DomainException.rule("the frame is not active");
        }
        if (stock < quantity) {
            throw DomainException.rule("insufficient stock: " + stock + " available, " + quantity + " requested");
        }
        return withStock(stock - quantity);
    }

    /** Puts units back, either a supplier entry or the return of a released reservation. */
    public Frame restock(int quantity) {
        if ((long) stock + quantity > MAX_STOCK) {
            throw DomainException.rule("stock cannot exceed " + MAX_STOCK + " units");
        }
        return withStock(stock + quantity);
    }

    public Frame withMinStock(int newMin) {
        quantityOrThrow(newMin, "minStock", 0);
        return new Frame(id, sku, brand, model, color, material, gender, costCents, salePriceCents, stock, newMin,
                location, supplier, status, createdAt, imageUrl);
    }

    /** Points the frame to its uploaded photo (a public relative path, never the disk location). */
    public Frame withImageUrl(String newImageUrl) {
        return new Frame(id, sku, brand, model, color, material, gender, costCents, salePriceCents, stock, minStock,
                location, supplier, status, createdAt, newImageUrl);
    }

    private Frame withStock(int newStock) {
        return new Frame(id, sku, brand, model, color, material, gender, costCents, salePriceCents, newStock, minStock,
                location, supplier, status, createdAt, imageUrl);
    }

    /** A frame at or below its minimum needs restocking. */
    public boolean lowStock() {
        return stock <= minStock;
    }

    public String description() {
        return "Frame " + brand + " " + model;
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

    public String model() {
        return model;
    }

    public String color() {
        return color;
    }

    public String material() {
        return material;
    }

    public String gender() {
        return gender;
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

    public String location() {
        return location;
    }

    public String supplier() {
        return supplier;
    }

    public FrameStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String imageUrl() {
        return imageUrl;
    }

    /** Raw input to register a frame, before validation. */
    public record RegisterData(String sku, String brand, String model, String color, String material, String gender,
                               Long costCents, Long salePriceCents, Integer stock, Integer minStock, String location,
                               String supplier) {
    }
}
