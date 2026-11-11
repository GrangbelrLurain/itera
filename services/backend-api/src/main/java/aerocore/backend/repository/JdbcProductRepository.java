package aerocore.backend.repository;

import aerocore.backend.model.DepartureRow;
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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** SQL Server implementation. Tables are created by infra/db/002-product-schema.sql. */
public class JdbcProductRepository implements ProductRepository {

    private final String url;
    private final String user;
    private final String password;

    public JdbcProductRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public Optional<ProductData> findProduct(String code) {
        try (Connection c = open()) {
            Product product = query(c, "SELECT * FROM dbo.product WHERE product_code=?", code,
                    JdbcProductRepository::product).stream().findFirst().orElse(null);
            if (product == null) return Optional.empty();
            return Optional.of(new ProductData(
                    product,
                    prices(c, code),
                    rules(c, code),
                    exclusions(c, code),
                    query(c, "SELECT * FROM dbo.product_itinerary WHERE product_code=? ORDER BY day_no, sort_order",
                            code, JdbcProductRepository::itinerary),
                    query(c, "SELECT * FROM dbo.product_optional_tour WHERE product_code=? ORDER BY seq", code,
                            JdbcProductRepository::optionalTour),
                    query(c, "SELECT * FROM dbo.product_shopping WHERE product_code=? ORDER BY seq", code,
                            JdbcProductRepository::shopping),
                    query(c, "SELECT * FROM dbo.product_remark WHERE product_code=?", code,
                            JdbcProductRepository::remark).stream().findFirst().orElse(null),
                    query(c, "SELECT * FROM dbo.product_condition WHERE product_code=?", code,
                            JdbcProductRepository::condition).stream().findFirst().orElse(null),
                    pricing(c, code)));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load product " + code + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<ProductData> findActiveProductSummaries() {
        return summaries(true);
    }

    @Override
    public List<ProductData> findAllProductSummaries() {
        return summaries(false);
    }

    private List<ProductData> summaries(boolean activeOnly) {
        try (Connection c = open(); Statement st = c.createStatement()) {
            List<Product> products = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT * FROM dbo.product"
                    + (activeOnly ? " WHERE active = 1" : "") + " ORDER BY product_code")) {
                while (rs.next()) products.add(product(rs));
            }
            List<ProductData> out = new ArrayList<>();
            for (Product p : products) {
                out.add(new ProductData(p, prices(c, p.code()), rules(c, p.code()), exclusions(c, p.code()),
                        List.of(), List.of(), List.of(), null, null, pricing(c, p.code())));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list products: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<LocalDate, DepartureRow> findDepartureRows(String productCode) {
        Map<LocalDate, DepartureRow> out = new TreeMap<>();
        try (Connection c = open()) {
            for (DepartureRow r : query(c, "SELECT * FROM dbo.departure WHERE product_code=?", productCode,
                    JdbcProductRepository::departure)) {
                out.put(r.date(), r);
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load departures of " + productCode + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Map<LocalDate, DepartureRow>> findAllDepartureRows() {
        Map<String, Map<LocalDate, DepartureRow>> out = new HashMap<>();
        try (Connection c = open(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM dbo.departure")) {
            while (rs.next()) {
                DepartureRow r = departure(rs);
                out.computeIfAbsent(r.productCode(), k -> new TreeMap<>()).put(r.date(), r);
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load departures: " + e.getMessage(), e);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static <T> List<T> query(Connection c, String sql, String productCode, RowMapper<T> mapper)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        }
    }

    /** Cost rows + margin policy, or null when the product has neither (manual prices only). */
    private static ProductData.Pricing pricing(Connection c, String code) throws SQLException {
        List<ProductData.CostRow> costs = query(c,
                "SELECT * FROM dbo.product_cost_component WHERE product_code=? ORDER BY seq", code,
                rs -> new ProductData.CostRow(rs.getInt("seq"), rs.getString("component_type"),
                        rs.getString("target_type"), rs.getLong("cost"), rs.getString("note")));
        ProductData.Pricing policy = query(c, "SELECT * FROM dbo.product_pricing_policy WHERE product_code=?", code,
                rs -> new ProductData.Pricing(List.of(), rs.getInt("margin_bp"), rs.getLong("margin_fixed")))
                .stream().findFirst().orElse(null);
        Map<LocalDate, List<ProductData.CostRow>> overrides = new HashMap<>();
        for (Object[] o : query(c, "SELECT * FROM dbo.departure_cost_override WHERE product_code=? "
                + "ORDER BY departure_date, component_type, target_type", code,
                rs -> new Object[] {rs.getDate("departure_date").toLocalDate(),
                        new ProductData.CostRow(0, rs.getString("component_type"), rs.getString("target_type"),
                                rs.getLong("cost"), rs.getString("note"))})) {
            overrides.computeIfAbsent((LocalDate) o[0], k -> new ArrayList<>()).add((ProductData.CostRow) o[1]);
        }
        if (costs.isEmpty() && policy == null && overrides.isEmpty()) return null;
        return new ProductData.Pricing(costs, policy == null ? 0 : policy.marginBp(),
                policy == null ? 0 : policy.marginFixed(), overrides);
    }

    private static List<Price> prices(Connection c, String code) throws SQLException {
        return query(c, "SELECT * FROM dbo.product_price WHERE product_code=?", code, rs -> new Price(
                rs.getString("target_type"), rs.getLong("base_price"), rs.getLong("fuel_surcharge"),
                rs.getLong("taxes_and_charges"), rs.getBoolean("sales_available")));
    }

    private static List<Rule> rules(Connection c, String code) throws SQLException {
        return query(c, "SELECT * FROM dbo.departure_rule WHERE product_code=? ORDER BY rule_id", code,
                rs -> new Rule(rs.getLong("rule_id"), rs.getDate("valid_from").toLocalDate(),
                        rs.getDate("valid_to").toLocalDate(), weekdays(rs.getInt("weekdays")),
                        rs.getBoolean("active")));
    }

    private static Set<LocalDate> exclusions(Connection c, String code) throws SQLException {
        return new HashSet<>(query(c, "SELECT excluded_date FROM dbo.rule_exclusion WHERE product_code=?", code,
                rs -> rs.getDate(1).toLocalDate()));
    }

    /** bit0 = Monday ... bit6 = Sunday (same as DayOfWeek.getValue() - 1). */
    static Set<DayOfWeek> weekdays(int mask) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            if ((mask & (1 << (d.getValue() - 1))) != 0) days.add(d);
        }
        return days;
    }

    private static Product product(ResultSet rs) throws SQLException {
        return new Product(rs.getString("product_code"), rs.getString("name"), rs.getString("grade"),
                rs.getInt("nights"), rs.getInt("days"), rs.getString("departure_route"),
                rs.getString("departure_time"), rs.getString("return_route"), rs.getString("return_time"),
                rs.getBoolean("direct_flight"), rs.getBoolean("visa_required"), rs.getString("meeting_time"),
                rs.getString("meeting_location"), rs.getString("hotel_name"), nullable(rs, "hotel_stars", rs::getInt),
                rs.getString("highlight"), rs.getString("promotion_title"), rs.getLong("discount_amount"),
                rs.getInt("default_capacity"), rs.getBoolean("active"));
    }

    private static ItineraryRow itinerary(ResultSet rs) throws SQLException {
        return new ItineraryRow(rs.getInt("day_no"), rs.getInt("sort_order"), rs.getString("service_type"),
                rs.getString("place_name"), rs.getString("description"), rs.getBoolean("is_highlight"),
                rs.getBoolean("is_perk"));
    }

    private static OptionalTourRow optionalTour(ResultSet rs) throws SQLException {
        return new OptionalTourRow(rs.getInt("seq"), rs.getString("name"), rs.getString("place_name"),
                rs.getString("name_en"), rs.getString("summary"), rs.getString("details"), rs.getString("currency"),
                nullable(rs, "price_adult", rs::getDouble), nullable(rs, "price_child", rs::getDouble),
                nullable(rs, "min_participants", rs::getInt), nullable(rs, "duration_minutes", rs::getInt),
                nullable(rs, "included", rs::getBoolean), nullable(rs, "pre_sale", rs::getBoolean));
    }

    private static ShoppingRow shopping(ResultSet rs) throws SQLException {
        return new ShoppingRow(rs.getInt("seq"), rs.getString("item_name"), rs.getString("place_name"),
                nullable(rs, "duration_minutes", rs::getInt), rs.getBoolean("refundable"));
    }

    private static Remark remark(ResultSet rs) throws SQLException {
        return new Remark(rs.getString("price_note"), rs.getString("inclusions"), rs.getString("exclusions"),
                rs.getString("highlights"), rs.getString("booking_notice"), rs.getString("room_info"),
                rs.getString("travel_notice"), rs.getString("faq"));
    }

    private static Condition condition(ResultSet rs) throws SQLException {
        return new Condition(rs.getString("airline_seat_class"), rs.getBoolean("tour_leader"),
                rs.getString("local_guide"), rs.getBoolean("insurance_included"), rs.getInt("shopping_count"),
                nullable(rs, "optional_tour_included", rs::getBoolean), rs.getBoolean("admission_fee_included"),
                rs.getString("local_expenses_currency"), rs.getInt("local_expenses_adult"),
                rs.getInt("local_expenses_child"), rs.getInt("local_expenses_infant"), rs.getInt("single_room_fee"),
                rs.getInt("min_departure_population"), rs.getString("cancellation_policy"));
    }

    private static DepartureRow departure(ResultSet rs) throws SQLException {
        return new DepartureRow(rs.getString("product_code"), rs.getDate("departure_date").toLocalDate(),
                rs.getInt("capacity"), rs.getInt("reserved_seats"), DepartureRow.Status.valueOf(rs.getString("status")),
                rs.getBoolean("pinned"));
    }

    private interface ColumnGetter<T> {
        T get(String column) throws SQLException;
    }

    private static <T> T nullable(ResultSet rs, String column, ColumnGetter<T> getter) throws SQLException {
        T value = getter.get(column);
        return rs.wasNull() ? null : value;
    }
}
