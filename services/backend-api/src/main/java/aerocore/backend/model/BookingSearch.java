package aerocore.backend.model;

import java.time.LocalDate;
import java.util.Set;

/**
 * Admin booking list filter (all optional). from/to apply to the booking creation date (UTC, inclusive);
 * productCode/departureDate narrow to one product or one departure.
 * keyword matches booking code, contact name/phone or package name.
 */
public record BookingSearch(Set<PackageBookingData.Status> statuses, LocalDate from, LocalDate to,
                            String productCode, LocalDate departureDate, String keyword) {

    public static BookingSearch none() {
        return new BookingSearch(Set.of(), null, null, null, null, null);
    }
}
