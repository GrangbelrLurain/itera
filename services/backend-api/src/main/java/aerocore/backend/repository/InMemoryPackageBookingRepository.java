package aerocore.backend.repository;

import aerocore.backend.exception.InsufficientSeatsException;
import aerocore.backend.model.BookingPage;
import aerocore.backend.model.BookingSearch;
import aerocore.backend.model.PackageBookingData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory bookings with the same atomic semantics as the SQL implementation. By default the seat counters are
 * private to this repository (unit tests); the memory profile passes the product store so that the departure
 * calendar sees the reserved seats.
 */
public class InMemoryPackageBookingRepository implements PackageBookingRepository {

    private final Map<String, PackageBookingData> bookings = new HashMap<>();
    private final DepartureSeats seats;
    private final LocalSeats local;

    public InMemoryPackageBookingRepository() {
        this.local = new LocalSeats();
        this.seats = local;
    }

    public InMemoryPackageBookingRepository(DepartureSeats seats) {
        this.local = null;
        this.seats = seats;
    }

    /** Remaining seats of a departure; a date without a stored row has {@code defaultCapacity} free. */
    public synchronized int availableSeats(String productCode, LocalDate date, int defaultCapacity) {
        return local().available(productCode, date, defaultCapacity);
    }

    /** True once a departure row exists for the date (it is created by the first booking). */
    public synchronized boolean hasDepartureRow(String productCode, LocalDate date) {
        return local().has(productCode, date);
    }

    /** Forgets every booking (memory profile reset). */
    public synchronized void clear() {
        bookings.clear();
    }

    private LocalSeats local() {
        if (local == null) throw new UnsupportedOperationException("seats are kept by the product store");
        return local;
    }

    @Override
    public synchronized void createWithSeatHold(PackageBookingData data, int capacityIfNew) {
        seats.hold(data.productCode(), data.departureDate(), data.seatCount(), capacityIfNew);
        bookings.put(data.bookingCode(), data);
    }

    @Override
    public synchronized Optional<PackageBookingData> findByCode(String bookingCode) {
        return Optional.ofNullable(bookings.get(bookingCode));
    }

    @Override
    public synchronized boolean transition(PackageBookingData updated, PackageBookingData.Status expected,
                                           int seatsToRelease) {
        PackageBookingData current = bookings.get(updated.bookingCode());
        if (current == null || current.status() != expected) {
            return false;
        }
        bookings.put(updated.bookingCode(), updated);
        seats.release(updated.productCode(), updated.departureDate(), seatsToRelease);
        return true;
    }

    @Override
    public synchronized BookingPage search(BookingSearch s, int page, int size) {
        List<PackageBookingData> matched = bookings.values().stream()
                .filter(b -> s.statuses() == null || s.statuses().isEmpty() || s.statuses().contains(b.status()))
                .filter(b -> s.from() == null || !day(b).isBefore(s.from()))
                .filter(b -> s.to() == null || !day(b).isAfter(s.to()))
                .filter(b -> s.productCode() == null || s.productCode().equals(b.productCode()))
                .filter(b -> s.departureDate() == null || s.departureDate().equals(b.departureDate()))
                .filter(b -> matchesKeyword(b, s.keyword()))
                .sorted(Comparator.comparing(PackageBookingData::createdAt).reversed()
                        .thenComparing(PackageBookingData::bookingCode))
                .toList();
        int from = Math.min(page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        List<PackageBookingData> items = matched.subList(from, to).stream()
                .map(b -> b.withTravelers(List.of())).toList();
        return new BookingPage(items, matched.size());
    }

    @Override
    public synchronized Map<PackageBookingData.Status, Long> countByStatus() {
        Map<PackageBookingData.Status, Long> out = new EnumMap<>(PackageBookingData.Status.class);
        bookings.values().forEach(b -> out.merge(b.status(), 1L, Long::sum));
        return out;
    }

    @Override
    public synchronized long confirmedRevenue() {
        return bookings.values().stream()
                .filter(b -> b.status() == PackageBookingData.Status.CONFIRMED)
                .mapToLong(PackageBookingData::totalAmount).sum();
    }

    @Override
    public synchronized List<PackageBookingData> findPendingDueBefore(Instant cutoff) {
        return bookings.values().stream()
                .filter(b -> b.status() == PackageBookingData.Status.PENDING_PAYMENT && b.paymentDueAt().isBefore(cutoff))
                .sorted(Comparator.comparing(PackageBookingData::paymentDueAt))
                .toList();
    }

    @Override
    public synchronized long countPendingDueBetween(Instant from, Instant to) {
        return bookings.values().stream()
                .filter(b -> b.status() == PackageBookingData.Status.PENDING_PAYMENT
                        && !b.paymentDueAt().isBefore(from) && !b.paymentDueAt().isAfter(to))
                .count();
    }

    private static LocalDate day(PackageBookingData b) {
        return b.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static boolean matchesKeyword(PackageBookingData b, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String k = keyword.trim().toLowerCase();
        return (b.bookingCode() + " " + b.contactName() + " " + b.contactPhone() + " " + b.packageName())
                .toLowerCase().contains(k);
    }

    /** Seat counters private to the repository: "productCode|date" -> {capacity, reservedSeats}. */
    private static final class LocalSeats implements DepartureSeats {
        private final Map<String, int[]> departures = new HashMap<>();

        private static String key(String productCode, LocalDate date) {
            return productCode + "|" + date;
        }

        int available(String productCode, LocalDate date, int defaultCapacity) {
            int[] row = departures.get(key(productCode, date));
            return row == null ? defaultCapacity : row[0] - row[1];
        }

        boolean has(String productCode, LocalDate date) {
            return departures.containsKey(key(productCode, date));
        }

        @Override
        public void hold(String productCode, LocalDate date, int count, int capacityIfNew) {
            int[] row = departures.computeIfAbsent(key(productCode, date), k -> new int[] {capacityIfNew, 0});
            if (row[0] - row[1] < count) {
                if (row[1] == 0) departures.remove(key(productCode, date)); // roll back the lazy row
                throw new InsufficientSeatsException(productCode, count);
            }
            row[1] += count;
        }

        @Override
        public void release(String productCode, LocalDate date, int count) {
            int[] row = departures.get(key(productCode, date));
            if (row != null) row[1] = Math.max(0, row[1] - count);
        }
    }
}
