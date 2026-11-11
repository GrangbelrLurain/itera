package aerocore.backend.controller;

import aerocore.backend.exception.ValidationException;
import aerocore.backend.service.PackageSearch;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lenient query parameter parsing: unknown or malformed filter values are ignored, never an error. */
final class Params {

    private Params() {}

    /** yyyy-MM-dd; null for blank or malformed input. */
    static LocalDate date(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static LocalDate requiredDate(String s, String message) {
        LocalDate d = date(s);
        if (d == null) throw new ValidationException(message);
        return d;
    }

    /** Accepts MON/TUE/... (first three letters, case-insensitive). */
    static DayOfWeek weekday(String s) {
        if (s == null || s.trim().length() < 3) return null;
        String prefix = s.trim().toUpperCase().substring(0, 3);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.name().startsWith(prefix)) return d;
        }
        return null;
    }

    static List<String> csv(String s) {
        return s == null ? List.of() : List.of(s.split(","));
    }

    static PackageSearch packageSearch(String from, String to, String keyword, String status, String weekdays) {
        Set<String> statuses = new HashSet<>();
        for (String s : csv(status)) {
            String t = s.trim().toLowerCase();
            if (t.equals("open") || t.equals("full") || t.equals("closed")) statuses.add(t);
        }
        Set<DayOfWeek> days = new HashSet<>();
        for (String s : csv(weekdays)) {
            DayOfWeek d = weekday(s);
            if (d != null) days.add(d);
        }
        return new PackageSearch(date(from), date(to), keyword, statuses, days);
    }
}
