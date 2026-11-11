package aerocore.backend.repository;

import aerocore.backend.model.BookingRecord;

import java.util.Collection;
import java.util.Optional;

/**
 * Persistence port for bookings. BookingService keeps an identity map of live records
 * (write-through), so implementations only need plain load/save semantics.
 */
public interface BookingRepository {

    /** Insert or update the booking (upsert by bookingId). */
    void save(BookingRecord record);

    Optional<BookingRecord> findById(String bookingId);

    Collection<BookingRecord> findAll();

    /** Remove all bookings (test/dev support). */
    void clear();
}
