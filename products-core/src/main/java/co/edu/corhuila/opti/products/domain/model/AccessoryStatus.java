package co.edu.corhuila.opti.products.domain.model;

/** An accessory is sold while ACTIVE; INACTIVE accessories stay in history but cannot be stocked further. */
public enum AccessoryStatus {
    ACTIVE,
    INACTIVE
}
