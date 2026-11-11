package aerocore.backend.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * List filter. Every field is optional (null / empty = no restriction).
 * statuses: "open" (accepting bookings), "full" (seats filled), "closed" (sales closed).
 */
public record PackageSearch(LocalDate from, LocalDate to, String keyword, Set<String> statuses,
                            Set<DayOfWeek> weekdays) {

    public static PackageSearch none() {
        return new PackageSearch(null, null, null, Set.of(), Set.of());
    }
}
