package aerocore.backend.repository;

import aerocore.backend.model.BookingRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Default repository when no database is configured (tests, quick local runs). */
public class InMemoryBookingRepository implements BookingRepository {

    private final ConcurrentHashMap<String, BookingRecord> store = new ConcurrentHashMap<>();

    @Override
    public void save(BookingRecord record) {
        store.put(record.getBookingId(), record);
    }

    @Override
    public Optional<BookingRecord> findById(String bookingId) {
        return Optional.ofNullable(store.get(bookingId));
    }

    @Override
    public Collection<BookingRecord> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void clear() {
        store.clear();
    }
}
