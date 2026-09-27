package co.edu.corhuila.opti.products.domain.model;

/** A frame is sold while ACTIVE; INACTIVE frames stay in history but cannot be reserved. */
public enum FrameStatus {
    ACTIVE,
    INACTIVE
}
