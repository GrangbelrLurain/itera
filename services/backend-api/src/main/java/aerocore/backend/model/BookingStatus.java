package aerocore.backend.model;

/**
 * Status enumeration for AeroCore booking lifecycle FSM.
 */
public enum BookingStatus {
    PENDING,
    SEAT_LOCKED,
    PAID,
    ISSUED,
    CANCELLED
}
