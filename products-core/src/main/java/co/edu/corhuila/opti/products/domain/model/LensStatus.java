package co.edu.corhuila.opti.products.domain.model;

/** A lens is sold while ACTIVE; INACTIVE lenses stay in history but cannot be stocked further. */
public enum LensStatus {
    ACTIVE,
    INACTIVE
}
