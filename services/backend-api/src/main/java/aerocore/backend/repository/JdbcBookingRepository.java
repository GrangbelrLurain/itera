package aerocore.backend.repository;

import aerocore.backend.model.BookingRecord;
import aerocore.backend.model.BookingStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * SQL Server (T-SQL) implementation using plain JDBC. The driver (mssql-jdbc) must be on the classpath.
 * The schema is created idempotently on startup.
 */
public class JdbcBookingRepository implements BookingRepository {

    private static final Logger LOGGER = Logger.getLogger(JdbcBookingRepository.class.getName());

    private static final String COLUMNS =
            "booking_id, pnr_code, flight_number, departure_date, passenger_name, passenger_passport_number, "
            + "total_amount, status, payment_due_timestamp, seat_number, ticket_number, payment_transaction_id, "
            + "cancel_reason, refund_amount, penalty_fee, created_at, issued_at, cancelled_at";

    private final String url;
    private final String user;
    private final String password;

    public JdbcBookingRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        waitForDatabaseAndInitSchema();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void waitForDatabaseAndInitSchema() {
        SQLException last = null;
        for (int attempt = 1; attempt <= 15; attempt++) {
            try (Connection c = open(); Statement st = c.createStatement()) {
                st.execute("IF OBJECT_ID(N'dbo.bookings', N'U') IS NULL CREATE TABLE dbo.bookings ("
                        + "booking_id NVARCHAR(32) NOT NULL PRIMARY KEY, "
                        + "pnr_code NVARCHAR(16) NOT NULL, "
                        + "flight_number NVARCHAR(16) NOT NULL, "
                        + "departure_date NVARCHAR(16) NOT NULL, "
                        + "passenger_name NVARCHAR(200) NOT NULL, "
                        + "passenger_passport_number NVARCHAR(64) NOT NULL, "
                        + "total_amount BIGINT NOT NULL, "
                        + "status NVARCHAR(16) NOT NULL, "
                        + "payment_due_timestamp BIGINT NOT NULL, "
                        + "seat_number NVARCHAR(8) NULL, "
                        + "ticket_number NVARCHAR(32) NULL, "
                        + "payment_transaction_id NVARCHAR(100) NULL, "
                        + "cancel_reason NVARCHAR(500) NULL, "
                        + "refund_amount BIGINT NOT NULL DEFAULT 0, "
                        + "penalty_fee BIGINT NOT NULL DEFAULT 0, "
                        + "created_at BIGINT NOT NULL, "
                        + "issued_at BIGINT NOT NULL DEFAULT 0, "
                        + "cancelled_at BIGINT NOT NULL DEFAULT 0)");
                // DB-level guard against double-booking a seat (cancelled bookings free the seat).
                st.execute("IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ux_bookings_active_seat' "
                        + "AND object_id = OBJECT_ID(N'dbo.bookings')) "
                        + "CREATE UNIQUE INDEX ux_bookings_active_seat ON dbo.bookings(flight_number, departure_date, seat_number) "
                        + "WHERE status <> N'CANCELLED' AND seat_number IS NOT NULL");
                LOGGER.info("JDBC booking repository ready: " + url);
                return;
            } catch (SQLException e) {
                last = e;
                LOGGER.warning("Database not ready (attempt " + attempt + "/15): " + e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("Cannot initialize database: " + (last != null ? last.getMessage() : "interrupted"), last);
    }

    @Override
    public void save(BookingRecord r) {
        String update = "UPDATE dbo.bookings SET status=?, seat_number=?, ticket_number=?, payment_transaction_id=?, "
                + "cancel_reason=?, refund_amount=?, penalty_fee=?, issued_at=?, cancelled_at=? WHERE booking_id=?";
        String insert = "INSERT INTO dbo.bookings (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = open()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setString(1, r.getStatus().name());
                ps.setString(2, r.getSeatNumber());
                ps.setString(3, emptyToNull(r.getTicketNumber()));
                ps.setString(4, r.getPaymentTransactionId());
                ps.setString(5, r.getCancelReason());
                ps.setLong(6, r.getRefundAmount());
                ps.setLong(7, r.getPenaltyFee());
                ps.setLong(8, r.getIssuedAt());
                ps.setLong(9, r.getCancelledAt());
                ps.setString(10, r.getBookingId());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = c.prepareStatement(insert)) {
                    ps.setString(1, r.getBookingId());
                    ps.setString(2, r.getPnrCode());
                    ps.setString(3, r.getFlightNumber());
                    ps.setString(4, r.getDepartureDate());
                    ps.setString(5, r.getPassengerName());
                    ps.setString(6, r.getPassengerPassportNumber());
                    ps.setLong(7, r.getTotalAmount());
                    ps.setString(8, r.getStatus().name());
                    ps.setLong(9, r.getPaymentDueTimestamp());
                    ps.setString(10, r.getSeatNumber());
                    ps.setString(11, emptyToNull(r.getTicketNumber()));
                    ps.setString(12, r.getPaymentTransactionId());
                    ps.setString(13, r.getCancelReason());
                    ps.setLong(14, r.getRefundAmount());
                    ps.setLong(15, r.getPenaltyFee());
                    ps.setLong(16, r.getCreatedAt());
                    ps.setLong(17, r.getIssuedAt());
                    ps.setLong(18, r.getCancelledAt());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save booking " + r.getBookingId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<BookingRecord> findById(String bookingId) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM dbo.bookings WHERE booking_id=?")) {
            ps.setString(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load booking " + bookingId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Collection<BookingRecord> findAll() {
        List<BookingRecord> out = new ArrayList<>();
        try (Connection c = open();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + COLUMNS + " FROM dbo.bookings")) {
            while (rs.next()) {
                out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load bookings: " + e.getMessage(), e);
        }
    }

    @Override
    public void clear() {
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM dbo.bookings");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear bookings: " + e.getMessage(), e);
        }
    }

    private static BookingRecord map(ResultSet rs) throws SQLException {
        return BookingRecord.restore(
                rs.getString("booking_id"),
                rs.getString("pnr_code"),
                rs.getString("flight_number"),
                rs.getString("departure_date"),
                rs.getString("passenger_name"),
                rs.getString("passenger_passport_number"),
                rs.getLong("total_amount"),
                BookingStatus.valueOf(rs.getString("status")),
                rs.getLong("payment_due_timestamp"),
                rs.getString("seat_number"),
                nullToEmpty(rs.getString("ticket_number")),
                rs.getString("payment_transaction_id"),
                rs.getString("cancel_reason"),
                rs.getLong("refund_amount"),
                rs.getLong("penalty_fee"),
                rs.getLong("created_at"),
                rs.getLong("issued_at"),
                rs.getLong("cancelled_at"));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
