package aerocore.backend.service;

import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.ProductData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Computes the departure dates of a product from its rules (virtual dates) merged with the stored rows.
 * Pure functions, no I/O, no side effects. Rules: docs/design/package-departure.md.
 *
 * V(product) = rule dates (active rule, valid period, weekday, not excluded, row not canceled)
 *            + stored rows that have bookings or are pinned (and are not canceled)
 * restricted to dates after "today".
 */
final class DepartureCalculator {

    /** Derived status of a departure date. */
    enum Status { OPEN, FULL, CLOSED }

    /** What a customer/admin sees for one date: stored values when a row exists, product defaults otherwise. */
    record Effective(LocalDate date, int capacity, int reservedSeats, int remainingSeats, Status status,
                     boolean stored) {}

    private DepartureCalculator() {}

    /** True when an active rule produces this date (ignores stored rows and "today"). */
    static boolean ruleProduces(ProductData product, LocalDate date) {
        if (product.exclusions().contains(date)) return false;
        for (ProductData.Rule r : product.rules()) {
            if (r.active() && !date.isBefore(r.validFrom()) && !date.isAfter(r.validTo())
                    && r.weekdays().contains(date.getDayOfWeek())) {
                return true;
            }
        }
        return false;
    }

    /** Is {@code date} a valid departure of the product (strictly after {@code today})? */
    static boolean isDeparture(ProductData product, Map<LocalDate, DepartureRow> rows, LocalDate date, LocalDate today) {
        if (!date.isAfter(today)) return false;
        DepartureRow row = rows.get(date);
        if (row != null && row.status() == DepartureRow.Status.CANCELED) return false;
        if (ruleProduces(product, date)) return true;
        return row != null && (row.reservedSeats() > 0 || row.pinned());
    }

    /** All departure dates in [from, to] (inclusive), ascending, only dates after {@code today}. */
    static List<LocalDate> dates(ProductData product, Map<LocalDate, DepartureRow> rows, LocalDate from, LocalDate to,
                                 LocalDate today) {
        LocalDate start = from.isAfter(today) ? from : today.plusDays(1);
        TreeSet<LocalDate> out = new TreeSet<>();
        for (ProductData.Rule r : product.rules()) {
            if (!r.active()) continue;
            LocalDate d = r.validFrom().isAfter(start) ? r.validFrom() : start;
            LocalDate end = r.validTo().isBefore(to) ? r.validTo() : to;
            for (; !d.isAfter(end); d = d.plusDays(1)) {
                if (r.weekdays().contains(d.getDayOfWeek())) out.add(d);
            }
        }
        for (DepartureRow row : rows.values()) {
            if (!row.date().isBefore(start) && !row.date().isAfter(to)) out.add(row.date());
        }
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate d : out) {
            if (isDeparture(product, rows, d, today)) result.add(d);
        }
        return result;
    }

    static Effective effective(ProductData product, DepartureRow row, LocalDate date) {
        if (row == null) {
            int cap = product.product().defaultCapacity();
            return new Effective(date, cap, 0, cap, cap > 0 ? Status.OPEN : Status.FULL, false);
        }
        int remaining = row.remainingSeats();
        Status status = row.status() == DepartureRow.Status.CLOSED
                ? Status.CLOSED
                : (remaining == 0 ? Status.FULL : Status.OPEN);
        return new Effective(date, row.capacity(), row.reservedSeats(), remaining, status, true);
    }
}
