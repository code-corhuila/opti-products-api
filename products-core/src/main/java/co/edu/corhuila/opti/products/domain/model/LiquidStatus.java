package co.edu.corhuila.opti.products.domain.model;

/** A liquid is sold while ACTIVE; INACTIVE liquids stay in history but cannot be stocked further. */
public enum LiquidStatus {
    ACTIVE,
    INACTIVE
}
