package aerocore.backend.repository;

import java.time.LocalDate;

/** Seat accounting of one departure; the in-memory booking repository delegates to it. */
public interface DepartureSeats {

    /**
     * Holds {@code seats} on the departure, creating its row with {@code capacityIfNew} seats if needed.
     * @throws aerocore.backend.exception.InsufficientSeatsException if fewer seats remain or the date is not open
     */
    void hold(String productCode, LocalDate date, int seats, int capacityIfNew);

    /** Gives seats back (never below zero reserved); no-op when the departure has no row. */
    void release(String productCode, LocalDate date, int seats);
}
