package aerocore.backend.repository.memory;

import aerocore.backend.exception.InsufficientSeatsException;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.DepartureRow.Status;
import aerocore.backend.model.ProductData;
import aerocore.backend.model.ProductData.Price;
import aerocore.backend.model.ProductData.Product;
import aerocore.backend.model.ProductData.Rule;
import aerocore.backend.repository.DepartureSeats;
import aerocore.backend.repository.ProductAdminRepository;
import aerocore.backend.repository.ProductRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Products and departures kept in memory (profile "memory": local runs and end-to-end tests without a database).
 * One store implements the read side, the admin side and the seat accounting, so a booking is visible in the
 * departure calendar exactly as it is with the SQL tables. Every method is synchronized on the store.
 */
public class MemoryProductStore implements ProductRepository, ProductAdminRepository, DepartureSeats {

    private final Map<String, ProductData> products = new TreeMap<>();
    private final Map<String, Map<LocalDate, DepartureRow>> rows = new HashMap<>();

    /** Adds or replaces a product (seed data). */
    public synchronized MemoryProductStore put(ProductData data) {
        products.put(data.product().code(), data);
        return this;
    }

    public synchronized void clear() {
        products.clear();
        rows.clear();
    }

    // ---- ProductRepository -----------------------------------------------------------------

    @Override
    public synchronized Optional<ProductData> findProduct(String code) {
        return Optional.ofNullable(products.get(code));
    }

    @Override
    public synchronized List<ProductData> findActiveProductSummaries() {
        return products.values().stream().filter(d -> d.product().active()).toList();
    }

    @Override
    public synchronized List<ProductData> findAllProductSummaries() {
        return new ArrayList<>(products.values());
    }

    @Override
    public synchronized Map<LocalDate, DepartureRow> findDepartureRows(String code) {
        return new TreeMap<>(rows.getOrDefault(code, Map.of()));
    }

    @Override
    public synchronized Map<String, Map<LocalDate, DepartureRow>> findAllDepartureRows() {
        Map<String, Map<LocalDate, DepartureRow>> copy = new HashMap<>();
        rows.forEach((code, byDate) -> copy.put(code, new TreeMap<>(byDate)));
        return copy;
    }

    // ---- ProductAdminRepository ------------------------------------------------------------

    @Override
    public synchronized boolean exists(String code) {
        return products.containsKey(code);
    }

    @Override
    public synchronized void insertProduct(Product p, List<Price> prices) {
        products.put(p.code(), new ProductData(p, prices, List.of(), Set.of(), List.of(), List.of(), List.of(),
                null, null));
    }

    @Override
    public synchronized void updateProduct(Product p, List<Price> prices) {
        ProductData o = products.get(p.code());
        products.put(p.code(), new ProductData(p, prices, o.rules(), o.exclusions(), o.itinerary(),
                o.optionalTours(), o.shoppings(), o.remark(), o.condition(), o.pricing()));
    }

    @Override
    public synchronized void setActive(String code, boolean active) {
        ProductData o = products.get(code);
        Product p = o.product();
        Product changed = new Product(p.code(), p.name(), p.grade(), p.nights(), p.days(), p.departureRoute(),
                p.departureTime(), p.returnRoute(), p.returnTime(), p.directFlight(), p.visaRequired(),
                p.meetingTime(), p.meetingLocation(), p.hotelName(), p.hotelStars(), p.highlight(),
                p.promotionTitle(), p.discountAmount(), p.defaultCapacity(), active);
        products.put(code, new ProductData(changed, o.prices(), o.rules(), o.exclusions(), o.itinerary(),
                o.optionalTours(), o.shoppings(), o.remark(), o.condition(), o.pricing()));
    }

    @Override
    public synchronized void replaceItinerary(String code, List<ProductData.ItineraryRow> itinerary) {
        ProductData o = products.get(code);
        products.put(code, new ProductData(o.product(), o.prices(), o.rules(), o.exclusions(), itinerary,
                o.optionalTours(), o.shoppings(), o.remark(), o.condition(), o.pricing()));
    }

    @Override
    public synchronized void replaceSchedule(String code, List<Rule> rules, Set<LocalDate> exclusions) {
        ProductData o = products.get(code);
        products.put(code, new ProductData(o.product(), o.prices(), rules, exclusions, o.itinerary(),
                o.optionalTours(), o.shoppings(), o.remark(), o.condition(), o.pricing()));
    }

    @Override
    public synchronized void replaceExtras(String code, List<ProductData.OptionalTourRow> tours,
                                           List<ProductData.ShoppingRow> shoppings, ProductData.Remark remark,
                                           ProductData.Condition condition) {
        ProductData o = products.get(code);
        products.put(code, new ProductData(o.product(), o.prices(), o.rules(), o.exclusions(), o.itinerary(),
                tours, shoppings, remark, condition, o.pricing()));
    }

    @Override
    public synchronized void replacePricing(String code, List<ProductData.CostRow> costs, int marginBp,
                                            long marginFixed) {
        ProductData o = products.get(code);
        Map<LocalDate, List<ProductData.CostRow>> overrides = o.pricing() == null ? Map.of() : o.pricing().overrides();
        products.put(code, new ProductData(o.product(), o.prices(), o.rules(), o.exclusions(), o.itinerary(),
                o.optionalTours(), o.shoppings(), o.remark(), o.condition(),
                new ProductData.Pricing(costs, marginBp, marginFixed, overrides)));
    }

    @Override
    public synchronized void replaceCostOverrides(String code, LocalDate date, List<ProductData.CostRow> costRows,
                                                  int capacityIfNew) {
        DepartureRow r = ensure(code, date, capacityIfNew);
        store(new DepartureRow(code, date, r.capacity(), r.reservedSeats(), r.status(), true));
        ProductData o = products.get(code);
        ProductData.Pricing p = o.pricing() == null ? new ProductData.Pricing(List.of(), 0, 0) : o.pricing();
        Map<LocalDate, List<ProductData.CostRow>> overrides = new HashMap<>(p.overrides());
        if (costRows.isEmpty()) overrides.remove(date);
        else overrides.put(date, costRows);
        products.put(code, new ProductData(o.product(), o.prices(), o.rules(), o.exclusions(), o.itinerary(),
                o.optionalTours(), o.shoppings(), o.remark(), o.condition(),
                new ProductData.Pricing(p.costs(), p.marginBp(), p.marginFixed(), overrides)));
    }

    @Override
    public synchronized void closeDeparture(String code, LocalDate date, int capacityIfNew) {
        DepartureRow r = ensure(code, date, capacityIfNew);
        store(new DepartureRow(code, date, r.capacity(), r.reservedSeats(), Status.CLOSED, true));
    }

    @Override
    public synchronized void reopenDeparture(String code, LocalDate date) {
        DepartureRow r = rows.getOrDefault(code, Map.of()).get(date);
        if (r != null && r.status() != Status.OPEN) {
            store(new DepartureRow(code, date, r.capacity(), r.reservedSeats(), Status.OPEN, r.pinned()));
        }
    }

    @Override
    public synchronized boolean setCapacity(String code, LocalDate date, int capacity, int capacityIfNew) {
        DepartureRow r = ensure(code, date, capacityIfNew);
        if (r.reservedSeats() > capacity) return false;
        store(new DepartureRow(code, date, capacity, r.reservedSeats(), r.status(), true));
        return true;
    }

    @Override
    public synchronized boolean cancelDeparture(String code, LocalDate date, int capacityIfNew) {
        DepartureRow r = ensure(code, date, capacityIfNew);
        if (r.reservedSeats() > 0) return false;
        store(new DepartureRow(code, date, r.capacity(), 0, Status.CANCELED, true));
        return true;
    }

    @Override
    public synchronized void addDeparture(String code, LocalDate date, int capacityIfNew) {
        DepartureRow r = ensure(code, date, capacityIfNew);
        store(new DepartureRow(code, date, r.capacity(), r.reservedSeats(), r.status(), true));
    }

    // ---- DepartureSeats (used by the in-memory booking repository) ---------------------------

    @Override
    public synchronized void hold(String code, LocalDate date, int seats, int capacityIfNew) {
        DepartureRow r = rows.getOrDefault(code, Map.of()).get(date);
        int capacity = r == null ? capacityIfNew : r.capacity();
        int reserved = r == null ? 0 : r.reservedSeats();
        Status status = r == null ? Status.OPEN : r.status();
        if (status != Status.OPEN || capacity - reserved < seats) {
            throw new InsufficientSeatsException(code, seats);
        }
        // the first booking of a date stores its row, unpinned (storage rule S2 of package-departure.md)
        store(new DepartureRow(code, date, capacity, reserved + seats, status, r != null && r.pinned()));
    }

    @Override
    public synchronized void release(String code, LocalDate date, int seats) {
        DepartureRow r = rows.getOrDefault(code, Map.of()).get(date);
        if (r == null) return;
        store(new DepartureRow(code, date, r.capacity(), Math.max(0, r.reservedSeats() - seats), r.status(),
                r.pinned()));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private DepartureRow ensure(String code, LocalDate date, int capacityIfNew) {
        Map<LocalDate, DepartureRow> byDate = rows.computeIfAbsent(code, k -> new TreeMap<>());
        return byDate.computeIfAbsent(date, d -> new DepartureRow(code, d, capacityIfNew, 0, Status.OPEN, false));
    }

    private void store(DepartureRow r) {
        rows.computeIfAbsent(r.productCode(), k -> new TreeMap<>()).put(r.date(), r);
    }
}
