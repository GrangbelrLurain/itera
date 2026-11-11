package aerocore.backend.repository;

import aerocore.backend.model.BookingPage;
import aerocore.backend.model.BookingSearch;
import aerocore.backend.model.PackageBookingData;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PackageBookingRepository {

    /**
     * Atomically holds {@code data.seatCount()} seats on the departure (product, date) and stores the booking.
     * The departure row is created in the same transaction when this is its first booking, with
     * {@code capacityIfNew} seats.
     * @throws aerocore.backend.exception.InsufficientSeatsException if fewer seats remain or the date is not open
     */
    void createWithSeatHold(PackageBookingData data, int capacityIfNew);

    Optional<PackageBookingData> findByCode(String bookingCode);

    /**
     * Stores {@code updated} only if the stored status is still {@code expected}, and releases
     * {@code seatsToRelease} seats in the same transaction.
     * @return false when the booking was changed concurrently (nothing is written)
     */
    boolean transition(PackageBookingData updated, PackageBookingData.Status expected, int seatsToRelease);

    // ---- admin queries ----------------------------------------------------------------------

    /** Newest first. {@code page} is 0-based. Returned items have an empty traveler list. */
    BookingPage search(BookingSearch search, int page, int size);

    Map<PackageBookingData.Status, Long> countByStatus();

    /** Sum of totalAmount over CONFIRMED bookings. */
    long confirmedRevenue();

    /** PENDING_PAYMENT bookings whose payment window ends before {@code cutoff} (oldest first). */
    List<PackageBookingData> findPendingDueBefore(Instant cutoff);

    /** Unpaid bookings whose payment window ends within [from, to] (both inclusive). */
    long countPendingDueBetween(Instant from, Instant to);
}
