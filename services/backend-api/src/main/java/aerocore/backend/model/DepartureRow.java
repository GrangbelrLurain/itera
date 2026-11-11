package aerocore.backend.model;

import java.time.LocalDate;

/**
 * A stored departure (one row per product and date). Rows exist only when a date has bookings or an explicit
 * admin action touched it; every other date is virtual (docs/design/package-departure.md).
 */
public record DepartureRow(String productCode, LocalDate date, int capacity, int reservedSeats, Status status,
                           boolean pinned) {

    public enum Status { OPEN, CLOSED, CANCELED }

    public int remainingSeats() {
        return Math.max(0, capacity - reservedSeats);
    }
}
