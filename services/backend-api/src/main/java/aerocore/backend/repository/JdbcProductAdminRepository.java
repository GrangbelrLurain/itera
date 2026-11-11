package aerocore.backend.repository;

import aerocore.backend.model.ProductData;
import aerocore.backend.model.ProductData.Condition;
import aerocore.backend.model.ProductData.ItineraryRow;
import aerocore.backend.model.ProductData.OptionalTourRow;
import aerocore.backend.model.ProductData.Price;
import aerocore.backend.model.ProductData.Product;
import aerocore.backend.model.ProductData.Remark;
import aerocore.backend.model.ProductData.Rule;
import aerocore.backend.model.ProductData.ShoppingRow;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** SQL Server implementation of the product write side. Every public method is one transaction. */
public class JdbcProductAdminRepository implements ProductAdminRepository {

    private static final int DUP_KEY = 2627;
    private static final int DUP_UNIQUE_INDEX = 2601;

    private final String url;
    private final String user;
    private final String password;

    public JdbcProductAdminRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private interface Work {
        void run(Connection c) throws SQLException;
    }

    private interface Query<T> {
        T run(Connection c) throws SQLException;
    }

    private void inTx(String what, Work work) {
        inTxResult(what, c -> {
            work.run(c);
            return null;
        });
    }

    private <T> T inTxResult(String what, Query<T> query) {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try {
                T result = query.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // connection is closed right after
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to " + what + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String code) {
        return inTxResult("check product " + code, c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM dbo.product WHERE product_code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    // ---- product ---------------------------------------------------------------------------

    @Override
    public void insertProduct(Product p, List<Price> prices) {
        inTx("create product " + p.code(), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.product (product_code, name, grade, nights, days, departure_route, departure_time, "
                            + "return_route, return_time, direct_flight, visa_required, meeting_time, meeting_location, "
                            + "hotel_name, hotel_stars, highlight, promotion_title, discount_amount, default_capacity, active) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, p.code());
                bindProduct(ps, p, 2);
                ps.executeUpdate();
            }
            insertPrices(c, p.code(), prices);
        });
    }

    @Override
    public void updateProduct(Product p, List<Price> prices) {
        inTx("update product " + p.code(), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE dbo.product SET name=?, grade=?, nights=?, days=?, departure_route=?, departure_time=?, "
                            + "return_route=?, return_time=?, direct_flight=?, visa_required=?, meeting_time=?, "
                            + "meeting_location=?, hotel_name=?, hotel_stars=?, highlight=?, promotion_title=?, "
                            + "discount_amount=?, default_capacity=?, active=? WHERE product_code=?")) {
                bindProduct(ps, p, 1);
                ps.setString(20, p.code());
                ps.executeUpdate();
            }
            delete(c, "dbo.product_price", p.code());
            insertPrices(c, p.code(), prices);
        });
    }

    /** Binds name .. active (19 values) starting at {@code from}. */
    private static void bindProduct(PreparedStatement ps, Product p, int from) throws SQLException {
        int i = from;
        ps.setString(i++, p.name());
        ps.setString(i++, p.grade());
        ps.setInt(i++, p.nights());
        ps.setInt(i++, p.days());
        ps.setString(i++, p.departureRoute());
        ps.setString(i++, p.departureTime());
        ps.setString(i++, p.returnRoute());
        ps.setString(i++, p.returnTime());
        ps.setBoolean(i++, p.directFlight());
        ps.setBoolean(i++, p.visaRequired());
        ps.setString(i++, p.meetingTime());
        ps.setString(i++, p.meetingLocation());
        ps.setString(i++, p.hotelName());
        setNullableInt(ps, i++, p.hotelStars());
        ps.setString(i++, p.highlight());
        ps.setString(i++, p.promotionTitle());
        ps.setLong(i++, p.discountAmount());
        ps.setInt(i++, p.defaultCapacity());
        ps.setBoolean(i, p.active());
    }

    private static void insertPrices(Connection c, String code, List<Price> prices) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO dbo.product_price (product_code, target_type, base_price, fuel_surcharge, "
                        + "taxes_and_charges, sales_available) VALUES (?,?,?,?,?,?)")) {
            for (Price p : prices) {
                ps.setString(1, code);
                ps.setString(2, p.targetType());
                ps.setLong(3, p.basePrice());
                ps.setLong(4, p.fuelSurcharge());
                ps.setLong(5, p.taxesAndCharges());
                ps.setBoolean(6, p.salesAvailable());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void setActive(String code, boolean active) {
        inTx("change product status " + code, c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE dbo.product SET active = ? WHERE product_code = ?")) {
                ps.setBoolean(1, active);
                ps.setString(2, code);
                ps.executeUpdate();
            }
        });
    }

    // ---- itinerary / schedule / extras -----------------------------------------------------

    @Override
    public void replaceItinerary(String code, List<ItineraryRow> rows) {
        inTx("save itinerary of " + code, c -> {
            delete(c, "dbo.product_itinerary", code);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.product_itinerary (product_code, day_no, sort_order, service_type, place_name, "
                            + "description, is_highlight, is_perk) VALUES (?,?,?,?,?,?,?,?)")) {
                for (ItineraryRow r : rows) {
                    ps.setString(1, code);
                    ps.setInt(2, r.dayNo());
                    ps.setInt(3, r.sortOrder());
                    ps.setString(4, r.serviceType());
                    ps.setString(5, r.placeName());
                    ps.setString(6, r.description());
                    ps.setBoolean(7, r.highlight());
                    ps.setBoolean(8, r.perk());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    @Override
    public void replaceSchedule(String code, List<Rule> rules, Set<LocalDate> exclusions) {
        inTx("save schedule of " + code, c -> {
            delete(c, "dbo.departure_rule", code);
            delete(c, "dbo.rule_exclusion", code);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.departure_rule (product_code, valid_from, valid_to, weekdays, active) "
                            + "VALUES (?,?,?,?,?)")) {
                for (Rule r : rules) {
                    ps.setString(1, code);
                    ps.setDate(2, Date.valueOf(r.validFrom()));
                    ps.setDate(3, Date.valueOf(r.validTo()));
                    ps.setInt(4, mask(r.weekdays()));
                    ps.setBoolean(5, r.active());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.rule_exclusion (product_code, excluded_date) VALUES (?,?)")) {
                for (LocalDate d : exclusions) {
                    ps.setString(1, code);
                    ps.setDate(2, Date.valueOf(d));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    @Override
    public void replaceExtras(String code, List<OptionalTourRow> tours, List<ShoppingRow> shoppings, Remark remark,
                              Condition condition) {
        inTx("save extras of " + code, c -> {
            delete(c, "dbo.product_optional_tour", code);
            delete(c, "dbo.product_shopping", code);
            delete(c, "dbo.product_remark", code);
            delete(c, "dbo.product_condition", code);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.product_optional_tour (product_code, seq, name, place_name, name_en, summary, "
                            + "details, currency, price_adult, price_child, min_participants, duration_minutes, "
                            + "included, pre_sale) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (OptionalTourRow t : tours) {
                    ps.setString(1, code);
                    ps.setInt(2, t.seq());
                    ps.setString(3, t.name());
                    ps.setString(4, t.placeName());
                    ps.setString(5, t.nameEn());
                    ps.setString(6, t.summary());
                    ps.setString(7, t.details());
                    ps.setString(8, t.currency());
                    setNullableDouble(ps, 9, t.priceAdult());
                    setNullableDouble(ps, 10, t.priceChild());
                    setNullableInt(ps, 11, t.minParticipants());
                    setNullableInt(ps, 12, t.durationMinutes());
                    setNullableBool(ps, 13, t.included());
                    setNullableBool(ps, 14, t.preSale());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.product_shopping (product_code, seq, item_name, place_name, duration_minutes, "
                            + "refundable) VALUES (?,?,?,?,?,?)")) {
                for (ShoppingRow s : shoppings) {
                    ps.setString(1, code);
                    ps.setInt(2, s.seq());
                    ps.setString(3, s.itemName());
                    ps.setString(4, s.placeName());
                    setNullableInt(ps, 5, s.durationMinutes());
                    ps.setBoolean(6, s.refundable());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            if (remark != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.product_remark (product_code, price_note, inclusions, exclusions, highlights, "
                                + "booking_notice, room_info, travel_notice, faq) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, code);
                    ps.setString(2, remark.priceNote());
                    ps.setString(3, remark.inclusions());
                    ps.setString(4, remark.exclusions());
                    ps.setString(5, remark.highlights());
                    ps.setString(6, remark.bookingNotice());
                    ps.setString(7, remark.roomInfo());
                    ps.setString(8, remark.travelNotice());
                    ps.setString(9, remark.faq());
                    ps.executeUpdate();
                }
            }
            if (condition != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.product_condition (product_code, airline_seat_class, tour_leader, local_guide, "
                                + "insurance_included, shopping_count, optional_tour_included, admission_fee_included, "
                                + "local_expenses_currency, local_expenses_adult, local_expenses_child, "
                                + "local_expenses_infant, single_room_fee, min_departure_population, cancellation_policy) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, code);
                    ps.setString(2, condition.airlineSeatClass());
                    ps.setBoolean(3, condition.tourLeader());
                    ps.setString(4, condition.localGuide());
                    ps.setBoolean(5, condition.insuranceIncluded());
                    ps.setInt(6, condition.shoppingCount());
                    setNullableBool(ps, 7, condition.optionalTourIncluded());
                    ps.setBoolean(8, condition.admissionFeeIncluded());
                    ps.setString(9, condition.localExpensesCurrency());
                    ps.setInt(10, condition.localExpensesAdult());
                    ps.setInt(11, condition.localExpensesChild());
                    ps.setInt(12, condition.localExpensesInfant());
                    ps.setInt(13, condition.singleRoomFee());
                    ps.setInt(14, condition.minDeparturePopulation());
                    ps.setString(15, condition.cancellationPolicy());
                    ps.executeUpdate();
                }
            }
        });
    }

    @Override
    public void replacePricing(String code, List<ProductData.CostRow> costs, int marginBp, long marginFixed) {
        inTx("save pricing of " + code, c -> {
            delete(c, "dbo.product_cost_component", code);
            delete(c, "dbo.product_pricing_policy", code);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.product_cost_component (product_code, seq, component_type, target_type, cost, note) "
                            + "VALUES (?,?,?,?,?,?)")) {
                for (ProductData.CostRow r : costs) {
                    ps.setString(1, code);
                    ps.setInt(2, r.seq());
                    ps.setString(3, r.componentType());
                    ps.setString(4, r.targetType());
                    ps.setLong(5, r.cost());
                    ps.setString(6, r.note());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.product_pricing_policy (product_code, margin_bp, margin_fixed) VALUES (?,?,?)")) {
                ps.setString(1, code);
                ps.setInt(2, marginBp);
                ps.setLong(3, marginFixed);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void replaceCostOverrides(String code, LocalDate date, List<ProductData.CostRow> rows, int capacityIfNew) {
        inTx("save cost overrides", c -> {
            ensureRow(c, code, date, capacityIfNew);
            update(c, "UPDATE dbo.departure SET pinned = 1 WHERE product_code = ? AND departure_date = ?", code, date);
            update(c, "DELETE FROM dbo.departure_cost_override WHERE product_code = ? AND departure_date = ?", code, date);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.departure_cost_override (product_code, departure_date, component_type, target_type, "
                            + "cost, note) VALUES (?,?,?,?,?,?)")) {
                for (ProductData.CostRow r : rows) {
                    ps.setString(1, code);
                    ps.setDate(2, Date.valueOf(date));
                    ps.setString(3, r.componentType());
                    ps.setString(4, r.targetType());
                    ps.setLong(5, r.cost());
                    ps.setString(6, r.note());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    // ---- departures ------------------------------------------------------------------------

    @Override
    public void closeDeparture(String code, LocalDate date, int capacityIfNew) {
        inTx("close departure", c -> {
            ensureRow(c, code, date, capacityIfNew);
            update(c, "UPDATE dbo.departure SET status = N'CLOSED', pinned = 1 "
                    + "WHERE product_code = ? AND departure_date = ?", code, date);
        });
    }

    @Override
    public void reopenDeparture(String code, LocalDate date) {
        inTx("reopen departure", c -> update(c,
                "UPDATE dbo.departure SET status = N'OPEN' WHERE product_code = ? AND departure_date = ? "
                        + "AND status IN (N'CLOSED', N'CANCELED')", code, date));
    }

    @Override
    public boolean setCapacity(String code, LocalDate date, int capacity, int capacityIfNew) {
        return inTxResult("change capacity", c -> {
            ensureRow(c, code, date, capacityIfNew);
            // The condition keeps capacity >= reserved seats (the table CHECK is the last line of defence).
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE dbo.departure SET capacity = ?, pinned = 1 "
                            + "WHERE product_code = ? AND departure_date = ? AND reserved_seats <= ?")) {
                ps.setInt(1, capacity);
                ps.setString(2, code);
                ps.setDate(3, Date.valueOf(date));
                ps.setInt(4, capacity);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean cancelDeparture(String code, LocalDate date, int capacityIfNew) {
        return inTxResult("cancel departure", c -> {
            ensureRow(c, code, date, capacityIfNew);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE dbo.departure SET status = N'CANCELED', pinned = 1 "
                            + "WHERE product_code = ? AND departure_date = ? AND reserved_seats = 0")) {
                ps.setString(1, code);
                ps.setDate(2, Date.valueOf(date));
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public void addDeparture(String code, LocalDate date, int capacityIfNew) {
        inTx("add departure", c -> {
            ensureRow(c, code, date, capacityIfNew);
            update(c, "UPDATE dbo.departure SET pinned = 1 WHERE product_code = ? AND departure_date = ?", code, date);
        });
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Same lazy-create rule as the booking repository (the primary key resolves races). */
    private static void ensureRow(Connection c, String code, LocalDate date, int capacity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "IF NOT EXISTS (SELECT 1 FROM dbo.departure WHERE product_code = ? AND departure_date = ?) "
                        + "INSERT INTO dbo.departure (product_code, departure_date, capacity, reserved_seats, status, pinned) "
                        + "VALUES (?, ?, ?, 0, N'OPEN', 0)")) {
            ps.setString(1, code);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, code);
            ps.setDate(4, Date.valueOf(date));
            ps.setInt(5, capacity);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() != DUP_KEY && e.getErrorCode() != DUP_UNIQUE_INDEX) throw e;
        }
    }

    private static void update(Connection c, String sql, String code, LocalDate date) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setDate(2, Date.valueOf(date));
            ps.executeUpdate();
        }
    }

    private static void delete(Connection c, String table, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE product_code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        }
    }

    /** bit0 = Monday ... bit6 = Sunday. */
    static int mask(Set<DayOfWeek> days) {
        int m = 0;
        for (DayOfWeek d : days) m |= 1 << (d.getValue() - 1);
        return m;
    }

    private static void setNullableInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER);
        else ps.setInt(i, v);
    }

    private static void setNullableDouble(PreparedStatement ps, int i, Double v) throws SQLException {
        if (v == null) ps.setNull(i, Types.DECIMAL);
        else ps.setDouble(i, v);
    }

    private static void setNullableBool(PreparedStatement ps, int i, Boolean v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BIT);
        else ps.setBoolean(i, v);
    }
}
