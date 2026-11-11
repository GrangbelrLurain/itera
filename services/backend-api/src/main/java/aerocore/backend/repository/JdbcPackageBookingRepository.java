package aerocore.backend.repository;

import aerocore.backend.exception.InsufficientSeatsException;
import aerocore.backend.model.BookingPage;
import aerocore.backend.model.BookingSearch;
import aerocore.backend.model.PackageBookingData;
import aerocore.backend.model.PackageBookingData.Status;
import aerocore.backend.model.PackageBookingData.Traveler;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQL Server implementation. Tables: infra/db/002-product-schema.sql (departure) and 005-package-booking.sql.
 * Departure rows are created lazily by the first booking of a date (docs/design/package-departure.md).
 */
public class JdbcPackageBookingRepository implements PackageBookingRepository {

    /** SQL Server error numbers for a duplicate key (PK / unique index). */
    private static final int DUP_KEY = 2627;
    private static final int DUP_UNIQUE_INDEX = 2601;

    private final String url;
    private final String user;
    private final String password;

    public JdbcPackageBookingRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public void createWithSeatHold(PackageBookingData b, int capacityIfNew) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                ensureDepartureRow(c, b.productCode(), b.departureDate(), capacityIfNew);
                // Atomic seat hold: fails (0 rows) when the date is not open or has too few seats left.
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE dbo.departure SET reserved_seats = reserved_seats + ? "
                                + "WHERE product_code = ? AND departure_date = ? AND status = N'OPEN' "
                                + "AND reserved_seats + ? <= capacity")) {
                    ps.setInt(1, b.seatCount());
                    ps.setString(2, b.productCode());
                    ps.setDate(3, Date.valueOf(b.departureDate()));
                    ps.setInt(4, b.seatCount());
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        throw new InsufficientSeatsException(b.productCode(), b.seatCount());
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.package_booking (booking_code, product_code, departure_date, status, "
                                + "channel_code, package_name, contact_name, contact_phone, contact_email, seat_count, "
                                + "total_amount, refund_amount, penalty_amount, payment_reference, cancel_reason, "
                                + "created_at, payment_due_at, confirmed_at, cancelled_at) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, b.bookingCode());
                    ps.setString(2, b.productCode());
                    ps.setDate(3, Date.valueOf(b.departureDate()));
                    ps.setString(4, b.status().name());
                    ps.setString(5, b.channelCode());
                    ps.setString(6, b.packageName());
                    ps.setString(7, b.contactName());
                    ps.setString(8, b.contactPhone());
                    ps.setString(9, b.contactEmail());
                    ps.setInt(10, b.seatCount());
                    ps.setLong(11, b.totalAmount());
                    ps.setLong(12, b.refundAmount());
                    ps.setLong(13, b.penaltyAmount());
                    ps.setString(14, b.paymentReference());
                    ps.setString(15, b.cancelReason());
                    ps.setTimestamp(16, ts(b.createdAt()));
                    ps.setTimestamp(17, ts(b.paymentDueAt()));
                    ps.setTimestamp(18, ts(b.confirmedAt()));
                    ps.setTimestamp(19, ts(b.cancelledAt()));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.package_booking_traveler (booking_code, seq, target_type, full_name, "
                                + "birth_date, gender, unit_price, unit_sale_price, unit_discount, unit_pass_through, "
                                + "unit_cost, unit_commission) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    for (Traveler t : b.travelers()) {
                        ps.setString(1, b.bookingCode());
                        ps.setInt(2, t.seq());
                        ps.setString(3, t.targetType());
                        ps.setString(4, t.fullName());
                        ps.setDate(5, Date.valueOf(t.birthDate()));
                        ps.setString(6, t.gender());
                        ps.setLong(7, t.unitPrice());
                        ps.setLong(8, t.unitSalePrice());
                        ps.setLong(9, t.unitDiscount());
                        ps.setLong(10, t.unitPassThrough());
                        setNullableLong(ps, 11, t.unitCost());
                        setNullableLong(ps, 12, t.unitCommission());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create booking " + b.bookingCode() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Storage rule S1: the departure row is created by the first booking of a date. Two first bookings may race;
     * the primary key makes one INSERT fail, and that one simply uses the row the other created.
     */
    private static void ensureDepartureRow(Connection c, String productCode, java.time.LocalDate date, int capacity)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "IF NOT EXISTS (SELECT 1 FROM dbo.departure WHERE product_code = ? AND departure_date = ?) "
                        + "INSERT INTO dbo.departure (product_code, departure_date, capacity, reserved_seats, status, pinned) "
                        + "VALUES (?, ?, ?, 0, N'OPEN', 0)")) {
            ps.setString(1, productCode);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, productCode);
            ps.setDate(4, Date.valueOf(date));
            ps.setInt(5, capacity);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() != DUP_KEY && e.getErrorCode() != DUP_UNIQUE_INDEX) throw e;
            // lost the race: the row exists now
        }
    }

    @Override
    public Optional<PackageBookingData> findByCode(String code) {
        try (Connection c = open()) {
            PackageBookingData head;
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM dbo.package_booking WHERE booking_code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    head = map(rs);
                }
            }
            List<Traveler> travelers = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM dbo.package_booking_traveler WHERE booking_code = ? ORDER BY seq")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        travelers.add(new Traveler(rs.getInt("seq"), rs.getString("target_type"),
                                rs.getString("full_name"), rs.getDate("birth_date").toLocalDate(),
                                rs.getString("gender"), rs.getLong("unit_price"), rs.getLong("unit_sale_price"),
                                rs.getLong("unit_discount"), rs.getLong("unit_pass_through"),
                                nullableLong(rs, "unit_cost"), nullableLong(rs, "unit_commission")));
                    }
                }
            }
            return Optional.of(head.withTravelers(travelers));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load booking " + code + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean transition(PackageBookingData u, Status expected, int seatsToRelease) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE dbo.package_booking SET status=?, payment_reference=?, cancel_reason=?, "
                                + "refund_amount=?, penalty_amount=?, confirmed_at=?, cancelled_at=? "
                                + "WHERE booking_code=? AND status=?")) {
                    ps.setString(1, u.status().name());
                    ps.setString(2, u.paymentReference());
                    ps.setString(3, u.cancelReason());
                    ps.setLong(4, u.refundAmount());
                    ps.setLong(5, u.penaltyAmount());
                    ps.setTimestamp(6, ts(u.confirmedAt()));
                    ps.setTimestamp(7, ts(u.cancelledAt()));
                    ps.setString(8, u.bookingCode());
                    ps.setString(9, expected.name());
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return false; // someone else changed the booking first
                    }
                }
                if (seatsToRelease > 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE dbo.departure SET reserved_seats = reserved_seats - ? "
                                    + "WHERE product_code = ? AND departure_date = ? AND reserved_seats >= ?")) {
                        ps.setInt(1, seatsToRelease);
                        ps.setString(2, u.productCode());
                        ps.setDate(3, Date.valueOf(u.departureDate()));
                        ps.setInt(4, seatsToRelease);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update booking " + u.bookingCode() + ": " + e.getMessage(), e);
        }
    }

    // ---- admin queries ----------------------------------------------------------------------

    @Override
    public BookingPage search(BookingSearch s, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (s.statuses() != null && !s.statuses().isEmpty()) {
            where.append(" AND status IN (")
                    .append(String.join(",", Collections.nCopies(s.statuses().size(), "?"))).append(")");
            s.statuses().forEach(st -> args.add(st.name()));
        }
        if (s.from() != null) {
            where.append(" AND created_at >= ?");
            args.add(Timestamp.from(s.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (s.to() != null) {
            where.append(" AND created_at < ?");
            args.add(Timestamp.from(s.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (s.productCode() != null && !s.productCode().isBlank()) {
            where.append(" AND product_code = ?");
            args.add(s.productCode().trim());
        }
        if (s.departureDate() != null) {
            where.append(" AND departure_date = ?");
            args.add(Date.valueOf(s.departureDate()));
        }
        if (s.keyword() != null && !s.keyword().isBlank()) {
            String like = "%" + s.keyword().trim().replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_").replace("[", "\\[") + "%";
            where.append(" AND (booking_code LIKE ? ESCAPE '\\' OR contact_name LIKE ? ESCAPE '\\' "
                    + "OR contact_phone LIKE ? ESCAPE '\\' OR package_name LIKE ? ESCAPE '\\')");
            for (int i = 0; i < 4; i++) args.add(like);
        }
        try (Connection c = open()) {
            long total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM dbo.package_booking" + where)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<PackageBookingData> items = new ArrayList<>();
            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(page * size);
            pageArgs.add(size);
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM dbo.package_booking" + where
                    + " ORDER BY created_at DESC, booking_code OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
                bind(ps, pageArgs);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) items.add(map(rs));
                }
            }
            return new BookingPage(items, total);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to search bookings: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<Status, Long> countByStatus() {
        Map<Status, Long> out = new EnumMap<>(Status.class);
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(
                "SELECT status, COUNT(*) FROM dbo.package_booking GROUP BY status");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(Status.valueOf(rs.getString(1)), rs.getLong(2));
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count bookings: " + e.getMessage(), e);
        }
    }

    @Override
    public long confirmedRevenue() {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(total_amount), 0) FROM dbo.package_booking WHERE status = 'CONFIRMED'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to sum revenue: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PackageBookingData> findPendingDueBefore(Instant cutoff) {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM dbo.package_booking WHERE status = 'PENDING_PAYMENT' AND payment_due_at < ? "
                        + "ORDER BY payment_due_at")) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            try (ResultSet rs = ps.executeQuery()) {
                List<PackageBookingData> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load overdue bookings: " + e.getMessage(), e);
        }
    }

    @Override
    public long countPendingDueBetween(Instant from, Instant to) {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM dbo.package_booking WHERE status = 'PENDING_PAYMENT' "
                        + "AND payment_due_at >= ? AND payment_due_at <= ?")) {
            ps.setTimestamp(1, Timestamp.from(from));
            ps.setTimestamp(2, Timestamp.from(to));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count expiring bookings: " + e.getMessage(), e);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object a = args.get(i);
            if (a instanceof String str) ps.setString(i + 1, str);
            else if (a instanceof Long l) ps.setLong(i + 1, l);
            else if (a instanceof Integer n) ps.setInt(i + 1, n);
            else if (a instanceof Timestamp t) ps.setTimestamp(i + 1, t);
            else if (a instanceof Date d) ps.setDate(i + 1, d);
            else throw new IllegalArgumentException("Unsupported bind type: " + a.getClass());
        }
    }

    /** Booking row without travelers (callers that need them use findByCode). */
    private static PackageBookingData map(ResultSet rs) throws SQLException {
        return new PackageBookingData(
                rs.getString("booking_code"), rs.getString("product_code"), rs.getDate("departure_date").toLocalDate(),
                Status.valueOf(rs.getString("status")), rs.getString("channel_code"), rs.getString("package_name"),
                rs.getString("contact_name"), rs.getString("contact_phone"), rs.getString("contact_email"),
                rs.getInt("seat_count"), rs.getLong("total_amount"), rs.getLong("refund_amount"),
                rs.getLong("penalty_amount"), rs.getString("payment_reference"), rs.getString("cancel_reason"),
                instant(rs, "created_at"), instant(rs, "payment_due_at"), instant(rs, "confirmed_at"),
                instant(rs, "cancelled_at"), List.of());
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.BIGINT);
        else ps.setLong(index, value);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // connection is closed right after
        }
    }
}
