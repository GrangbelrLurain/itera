package aerocore.backend;

import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.ProductData;
import aerocore.backend.model.ProductData.Price;
import aerocore.backend.model.ProductData.Product;
import aerocore.backend.model.ProductData.Rule;
import aerocore.backend.repository.ProductRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Shared builders and fakes for the package tests (no database). */
final class TestFixtures {

    private TestFixtures() {}

    /** Clock whose time can be moved by the test. */
    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(String iso) { this.now = Instant.parse(iso); }

        void set(String iso) { this.now = Instant.parse(iso); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** 2027-01-04 is a Monday. */
    static final String TODAY = "2027-01-04T00:00:00Z";

    static Product product(String code, String name, int defaultCapacity, boolean active, long discount) {
        return new Product(code, name, "프리미엄", 4, 5, "ICN-DAD", "09:30", "DAD-ICN", "20:10",
                true, false, "07:30", "인천공항 제1터미널", "리조트", 5, "하이라이트", null,
                discount, defaultCapacity, active);
    }

    static Product product(String code, String name, int defaultCapacity) {
        return product(code, name, defaultCapacity, true, 50000);
    }

    static Rule rule(LocalDate from, LocalDate to, DayOfWeek... days) {
        Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek d : days) set.add(d);
        return new Rule(1, from, to, set, true);
    }

    static Rule inactive(Rule r) {
        return new Rule(r.ruleId(), r.validFrom(), r.validTo(), r.weekdays(), false);
    }

    /** Default sample prices: adult 1,000,000(+90,000 fuel +45,000 tax), no-bed 900,000, infant 150,000(+15,000 tax). */
    static List<Price> defaultPrices() {
        return List.of(
                new Price("ADULT", 1000000, 90000, 45000, true),
                new Price("NOBED_CHILD", 900000, 90000, 45000, true),
                new Price("INFANT", 150000, 0, 15000, true),
                new Price("EXTRABED_CHILD", 950000, 90000, 45000, false));
    }

    static ProductData data(Product p, List<Rule> rules, Set<LocalDate> exclusions) {
        return new ProductData(p, defaultPrices(), rules, exclusions,
                List.of(
                        new ProductData.ItineraryRow(2, 1, "SIGHTSEEING", "바나힐", "관광", false, true),
                        new ProductData.ItineraryRow(1, 1, "FLIGHT", "공항", "출발", false, false)),
                List.of(), List.of(), null, null);
    }

    static ProductData data(Product p, List<Rule> rules) {
        return data(p, rules, Set.of());
    }

    static DepartureRow row(String code, LocalDate date, int capacity, int reserved, DepartureRow.Status status,
                            boolean pinned) {
        return new DepartureRow(code, date, capacity, reserved, status, pinned);
    }

    /** In-memory ProductRepository; mutable so tests can add rows. */
    static final class FakeProducts implements ProductRepository {
        final Map<String, ProductData> products = new TreeMap<>();
        final Map<String, Map<LocalDate, DepartureRow>> rows = new HashMap<>();

        FakeProducts add(ProductData d, DepartureRow... storedRows) {
            products.put(d.product().code(), d);
            for (DepartureRow r : storedRows) {
                rows.computeIfAbsent(r.productCode(), k -> new TreeMap<>()).put(r.date(), r);
            }
            return this;
        }

        @Override public Optional<ProductData> findProduct(String code) {
            return Optional.ofNullable(products.get(code));
        }

        @Override public List<ProductData> findActiveProductSummaries() {
            List<ProductData> out = new ArrayList<>();
            products.values().stream().filter(d -> d.product().active()).forEach(out::add);
            return out;
        }

        @Override public List<ProductData> findAllProductSummaries() {
            return new ArrayList<>(products.values());
        }

        @Override public Map<LocalDate, DepartureRow> findDepartureRows(String code) {
            return rows.getOrDefault(code, Map.of());
        }

        @Override public Map<String, Map<LocalDate, DepartureRow>> findAllDepartureRows() {
            return rows;
        }
    }


    /** In-memory ProductAdminRepository working on a FakeProducts (same semantics as the SQL version). */
    static final class FakeAdmin implements aerocore.backend.repository.ProductAdminRepository {
        private final FakeProducts store;

        FakeAdmin(FakeProducts store) { this.store = store; }

        private void put(ProductData d) { store.products.put(d.product().code(), d); }

        @Override public boolean exists(String code) { return store.products.containsKey(code); }

        @Override public void insertProduct(Product p, List<Price> prices) {
            put(new ProductData(p, prices, List.of(), Set.of(), List.of(), List.of(), List.of(), null, null));
        }

        @Override public void updateProduct(Product p, List<Price> prices) {
            ProductData o = store.products.get(p.code());
            put(new ProductData(p, prices, o.rules(), o.exclusions(), o.itinerary(), o.optionalTours(),
                    o.shoppings(), o.remark(), o.condition(), o.pricing()));
        }

        @Override public void setActive(String code, boolean active) {
            ProductData o = store.products.get(code);
            Product p = o.product();
            put(new ProductData(new Product(p.code(), p.name(), p.grade(), p.nights(), p.days(), p.departureRoute(),
                    p.departureTime(), p.returnRoute(), p.returnTime(), p.directFlight(), p.visaRequired(),
                    p.meetingTime(), p.meetingLocation(), p.hotelName(), p.hotelStars(), p.highlight(),
                    p.promotionTitle(), p.discountAmount(), p.defaultCapacity(), active),
                    o.prices(), o.rules(), o.exclusions(), o.itinerary(), o.optionalTours(), o.shoppings(),
                    o.remark(), o.condition(), o.pricing()));
        }

        @Override public void replaceItinerary(String code, List<ProductData.ItineraryRow> rows) {
            ProductData o = store.products.get(code);
            put(new ProductData(o.product(), o.prices(), o.rules(), o.exclusions(), rows, o.optionalTours(),
                    o.shoppings(), o.remark(), o.condition(), o.pricing()));
        }

        @Override public void replaceSchedule(String code, List<Rule> rules, Set<LocalDate> exclusions) {
            ProductData o = store.products.get(code);
            put(new ProductData(o.product(), o.prices(), rules, exclusions, o.itinerary(), o.optionalTours(),
                    o.shoppings(), o.remark(), o.condition(), o.pricing()));
        }

        @Override public void replaceExtras(String code, List<ProductData.OptionalTourRow> tours,
                                            List<ProductData.ShoppingRow> shoppings, ProductData.Remark remark,
                                            ProductData.Condition condition) {
            ProductData o = store.products.get(code);
            put(new ProductData(o.product(), o.prices(), o.rules(), o.exclusions(), o.itinerary(), tours, shoppings,
                    remark, condition, o.pricing()));
        }

        @Override public void replacePricing(String code, List<ProductData.CostRow> costs, int marginBp,
                                             long marginFixed) {
            ProductData o = store.products.get(code);
            put(new ProductData(o.product(), o.prices(), o.rules(), o.exclusions(), o.itinerary(), o.optionalTours(),
                    o.shoppings(), o.remark(), o.condition(),
                    new ProductData.Pricing(costs, marginBp, marginFixed,
                            o.pricing() == null ? Map.of() : o.pricing().overrides())));
        }

        @Override public void replaceCostOverrides(String code, LocalDate date, List<ProductData.CostRow> rows,
                                                   int capacityIfNew) {
            DepartureRow r = ensure(code, date, capacityIfNew);
            replace(new DepartureRow(code, date, r.capacity(), r.reservedSeats(), r.status(), true));
            ProductData o = store.products.get(code);
            ProductData.Pricing p = o.pricing() == null ? new ProductData.Pricing(List.of(), 0, 0) : o.pricing();
            Map<LocalDate, List<ProductData.CostRow>> overrides = new HashMap<>(p.overrides());
            if (rows.isEmpty()) overrides.remove(date);
            else overrides.put(date, rows);
            put(new ProductData(o.product(), o.prices(), o.rules(), o.exclusions(), o.itinerary(), o.optionalTours(),
                    o.shoppings(), o.remark(), o.condition(),
                    new ProductData.Pricing(p.costs(), p.marginBp(), p.marginFixed(), overrides)));
        }

        private DepartureRow ensure(String code, LocalDate date, int capacityIfNew) {
            Map<LocalDate, DepartureRow> rows = store.rows.computeIfAbsent(code, k -> new TreeMap<>());
            return rows.computeIfAbsent(date, d -> new DepartureRow(code, d, capacityIfNew, 0,
                    DepartureRow.Status.OPEN, false));
        }

        private void replace(DepartureRow r) {
            store.rows.get(r.productCode()).put(r.date(), r);
        }

        @Override public void closeDeparture(String code, LocalDate date, int capacityIfNew) {
            DepartureRow r = ensure(code, date, capacityIfNew);
            replace(new DepartureRow(code, date, r.capacity(), r.reservedSeats(), DepartureRow.Status.CLOSED, true));
        }

        @Override public void reopenDeparture(String code, LocalDate date) {
            DepartureRow r = store.rows.getOrDefault(code, Map.of()).get(date);
            if (r != null && r.status() != DepartureRow.Status.OPEN) {
                replace(new DepartureRow(code, date, r.capacity(), r.reservedSeats(), DepartureRow.Status.OPEN, r.pinned()));
            }
        }

        @Override public boolean setCapacity(String code, LocalDate date, int capacity, int capacityIfNew) {
            DepartureRow r = ensure(code, date, capacityIfNew);
            if (r.reservedSeats() > capacity) return false;
            replace(new DepartureRow(code, date, capacity, r.reservedSeats(), r.status(), true));
            return true;
        }

        @Override public boolean cancelDeparture(String code, LocalDate date, int capacityIfNew) {
            DepartureRow r = ensure(code, date, capacityIfNew);
            if (r.reservedSeats() > 0) return false;
            replace(new DepartureRow(code, date, r.capacity(), 0, DepartureRow.Status.CANCELED, true));
            return true;
        }

        @Override public void addDeparture(String code, LocalDate date, int capacityIfNew) {
            DepartureRow r = ensure(code, date, capacityIfNew);
            replace(new DepartureRow(code, date, r.capacity(), r.reservedSeats(), r.status(), true));
        }
    }

    /** Cost row builder: seq is assigned by position when rows are listed through {@link #pricing}. */
    static ProductData.CostRow cost(int seq, String component, String target, long cost) {
        return new ProductData.CostRow(seq, component, target, cost, null);
    }

    /** Same product with cost-based pricing attached. */
    static ProductData withPricing(ProductData d, int marginBp, long marginFixed, ProductData.CostRow... costs) {
        return new ProductData(d.product(), d.prices(), d.rules(), d.exclusions(), d.itinerary(), d.optionalTours(),
                d.shoppings(), d.remark(), d.condition(),
                new ProductData.Pricing(List.of(costs), marginBp, marginFixed));
    }

    /** Same product with cost overrides for one date (the product must already have pricing). */
    static ProductData withOverride(ProductData d, LocalDate date, ProductData.CostRow... rows) {
        ProductData.Pricing p = d.pricing() == null ? new ProductData.Pricing(List.of(), 0, 0) : d.pricing();
        Map<LocalDate, List<ProductData.CostRow>> overrides = new HashMap<>(p.overrides());
        overrides.put(date, List.of(rows));
        return new ProductData(d.product(), d.prices(), d.rules(), d.exclusions(), d.itinerary(), d.optionalTours(),
                d.shoppings(), d.remark(), d.condition(),
                new ProductData.Pricing(p.costs(), p.marginBp(), p.marginFixed(), overrides));
    }

    /** In-memory channel commissions. */
    static final class FakeCommissions implements aerocore.backend.repository.ChannelCommissionRepository {
        final List<aerocore.backend.model.ChannelCommission> rows = new ArrayList<>();

        FakeCommissions add(String channel, String product, int bp, long fixed, boolean active) {
            rows.add(new aerocore.backend.model.ChannelCommission(channel, product, bp, fixed, active));
            return this;
        }

        @Override public List<aerocore.backend.model.ChannelCommission> findAll() { return new ArrayList<>(rows); }

        @Override public void replaceAll(List<aerocore.backend.model.ChannelCommission> newRows) {
            rows.clear();
            rows.addAll(newRows);
        }
    }

    static Set<LocalDate> setOf(LocalDate... dates) {
        Set<LocalDate> s = new HashSet<>();
        for (LocalDate d : dates) s.add(d);
        return s;
    }
}
