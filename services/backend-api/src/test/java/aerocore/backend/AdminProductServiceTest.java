package aerocore.backend;

import aerocore.backend.dto.AdminProductDtos.ConditionInput;
import aerocore.backend.dto.AdminProductDtos.DepartureItem;
import aerocore.backend.dto.AdminProductDtos.ExtrasInput;
import aerocore.backend.dto.AdminProductDtos.ItineraryInput;
import aerocore.backend.dto.AdminProductDtos.OptionalTourInput;
import aerocore.backend.dto.AdminProductDtos.PriceInput;
import aerocore.backend.dto.AdminProductDtos.ProductDetail;
import aerocore.backend.dto.AdminProductDtos.ProductInput;
import aerocore.backend.dto.AdminProductDtos.RemarkInput;
import aerocore.backend.dto.AdminProductDtos.RuleInput;
import aerocore.backend.dto.AdminProductDtos.ScheduleInput;
import aerocore.backend.dto.AdminProductDtos.ShoppingInput;
import aerocore.backend.exception.OperationNotAllowedException;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.exception.ProductExistsException;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.service.AdminProductService;
import aerocore.backend.service.CostBasedPriceCalculator;
import aerocore.backend.service.PackageSearch;
import aerocore.backend.service.PackageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static aerocore.backend.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Back-office product management: validation, schedule rules, departure actions (no database). */
public class AdminProductServiceTest {

    private MutableClock clock;
    private FakeProducts store;
    private AdminProductService admin;
    private PackageService packages;

    private void setUp() {
        clock = new MutableClock(TODAY);                    // 2027-01-04 (Monday)
        store = new FakeProducts();
        admin = new AdminProductService(store, new FakeAdmin(store), clock);
        packages = new PackageService(store, new CostBasedPriceCalculator(), clock);
    }

    private static List<PriceInput> prices() {
        return List.of(new PriceInput("ADULT", 1000000L, 90000L, 45000L, true),
                new PriceInput("INFANT", 150000L, 0L, 15000L, true));
    }

    /** A valid product body; tests tweak single fields through the helper below. */
    private static ProductInput input(String code) {
        return new ProductInput(code, "다낭 4박5일", "프리미엄", 4, 5, "ICN-DAD", "09:30", "DAD-ICN", "20:10",
                true, false, "07:30", "인천공항", "리조트", 5, "하이라이트", null, 50000L, 30, true, prices());
    }

    private static ProductInput with(ProductInput b, String name, Integer nights, Integer days, String time,
                                     Integer capacity, Integer stars, List<PriceInput> prices) {
        return new ProductInput(b.code(), name, b.grade(), nights, days, b.departureRoute(), time, b.returnRoute(),
                b.returnTime(), b.directFlight(), b.visaRequired(), b.meetingTime(), b.meetingLocation(),
                b.hotelName(), stars, b.highlight(), b.promotionTitle(), b.discountAmount(), capacity, b.active(),
                prices);
    }

    private static RuleInput rule(String from, String to, String... weekdays) {
        return new RuleInput(from, to, List.of(weekdays), true);
    }

    private ProductDetail createWithWednesdayRule(String code) {
        admin.createProduct(input(code));
        return admin.replaceSchedule(code, new ScheduleInput(List.of(rule("2027-03-01", "2027-05-31", "WED")), List.of()));
    }

    public static void main(String[] args) {
        AdminProductServiceTest t = new AdminProductServiceTest();
        t.testCreateProductValidationAndNormalization();
        t.testUpdateKeepsCodeAndReplacesPrices();
        t.testDeactivateKeepsProductReadableButUnlisted();
        t.testItineraryValidationAndOrdering();
        t.testScheduleValidation();
        t.testScheduleChangesNeverDropBookedDates();
        t.testExtrasValidationAndClearing();
        t.testDepartureCalendarIncludesRuleStoredAndCanceledDates();
        t.testCloseReopenAndCapacity();
        t.testCancelDepartureNeedsNoBookingsAndCanBeReopened();
        t.testAddExtraDeparture();
        t.testDepartureActionsRejectNonDepartureDates();
        System.out.println("✔ AdminProductServiceTest all assertions passed!");
    }

    @Test
    @DisplayName("Create: code rules, duplicates, required ADULT price, field ranges; code is upper-cased")
    public void testCreateProductValidationAndNormalization() {
        setUp();
        ProductDetail d = admin.createProduct(input("dad501"));
        assertEquals("DAD501", d.product().code());
        assertEquals(2, d.product().prices().size());
        assertEquals(0, d.schedule().rules().size());
        assertThrows(ProductExistsException.class, () -> admin.createProduct(input("DAD501")));
        assertThrows(ValidationException.class, () -> admin.createProduct(input("a")));              // too short
        assertThrows(ValidationException.class, () -> admin.createProduct(input("BAD CODE")));
        assertThrows(ValidationException.class, () -> admin.createProduct(null));

        ProductInput b = input("X1");
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, " ", 4, 5, "09:30", 30, 5, prices())));
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 3, "09:30", 30, 5, prices())));   // days < nights
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "9:30", 30, 5, prices())));    // time format
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "25:00", 30, 5, prices())));
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "09:30", 0, 5, prices())));    // capacity
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "09:30", 1000, 5, prices())));
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "09:30", 30, 6, prices())));   // stars
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "09:30", 30, 5,
                List.of(new PriceInput("INFANT", 1L, 0L, 0L, true)))));                                                   // no ADULT
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "09:30", 30, 5,
                List.of(new PriceInput("ADULT", -1L, 0L, 0L, true)))));
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "09:30", 30, 5,
                List.of(new PriceInput("ADULT", 1L, 0L, 0L, true), new PriceInput("ADULT", 2L, 0L, 0L, true)))));   // duplicate
        assertThrows(ValidationException.class, () -> admin.createProduct(with(b, "n", 4, 5, "09:30", 30, 5,
                List.of(new PriceInput("SENIOR", 1L, 0L, 0L, true)))));
        assertFalse(store.products.containsKey("X1"));                      // nothing was stored by failures
    }

    @Test
    @DisplayName("Update: the code is immutable and prices are replaced as a set")
    public void testUpdateKeepsCodeAndReplacesPrices() {
        setUp();
        admin.createProduct(input("DAD501"));
        ProductInput changed = with(input("DAD501"), "다낭 5박6일", 5, 6, "10:00", 40, 4,
                List.of(new PriceInput("ADULT", 2000000L, 100000L, 50000L, true)));
        ProductDetail d = admin.updateProduct("DAD501", changed);
        assertEquals("다낭 5박6일", d.product().name());
        assertEquals(40, d.product().defaultCapacity().intValue());
        assertEquals(1, d.product().prices().size());
        assertEquals(2000000L, d.product().prices().get(0).basePrice().longValue());
        assertThrows(ValidationException.class, () -> admin.updateProduct("DAD501", input("OTHER")));   // code change
        assertThrows(PackageNotFoundException.class, () -> admin.updateProduct("NOPE", input("NOPE")));
    }

    @Test
    @DisplayName("Deactivating hides the product from the public list but keeps it manageable")
    public void testDeactivateKeepsProductReadableButUnlisted() {
        setUp();
        createWithWednesdayRule("DAD501");
        assertTrue(packages.listPackages(PackageSearch.none(), 0, 100).total() > 0);
        ProductDetail d = admin.setActive("DAD501", false);
        assertFalse(d.product().active());
        assertEquals(0, packages.listPackages(PackageSearch.none(), 0, 100).total());
        assertEquals(1, admin.listProducts().size());                     // still in the admin list
        assertTrue(admin.setActive("DAD501", true).product().active());
        assertTrue(packages.listPackages(PackageSearch.none(), 0, 100).total() > 0);
    }

    @Test
    @DisplayName("Itinerary: day range, service types, content; per-day order follows the input order")
    public void testItineraryValidationAndOrdering() {
        setUp();
        admin.createProduct(input("DAD501"));                                // 5 days
        ProductDetail d = admin.replaceItinerary("DAD501", List.of(
                new ItineraryInput(2, "SIGHTSEEING", "바나힐", null, true, false),
                new ItineraryInput(1, "FLIGHT", "인천 → 다낭", "출발", false, false),
                new ItineraryInput(2, "MEAL", null, "현지식", false, true)));
        assertEquals(3, d.itinerary().size());
        assertEquals(2, d.itinerary().get(0).dayNo().intValue());                        // stored in the given order
        assertThrows(ValidationException.class, () -> admin.replaceItinerary("DAD501",
                List.of(new ItineraryInput(6, "FLIGHT", "x", null, false, false))));     // day 6 > 5 days
        assertThrows(ValidationException.class, () -> admin.replaceItinerary("DAD501",
                List.of(new ItineraryInput(0, "FLIGHT", "x", null, false, false))));
        assertThrows(ValidationException.class, () -> admin.replaceItinerary("DAD501",
                List.of(new ItineraryInput(1, "BOAT", "x", null, false, false))));       // unknown type
        assertThrows(ValidationException.class, () -> admin.replaceItinerary("DAD501",
                List.of(new ItineraryInput(1, "FLIGHT", " ", " ", false, false))));      // empty content
        List<ItineraryInput> tooMany = new ArrayList<>();
        for (int i = 0; i < 201; i++) tooMany.add(new ItineraryInput(1, "FREE", "x", null, false, false));
        assertThrows(ValidationException.class, () -> admin.replaceItinerary("DAD501", tooMany));
        assertEquals(3, admin.getProduct("DAD501").itinerary().size());          // failures changed nothing
        assertEquals(0, admin.replaceItinerary("DAD501", List.of()).itinerary().size());   // clearing is allowed
    }

    @Test
    @DisplayName("Schedule: dates, span, weekdays, counts; weekday names are flexible")
    public void testScheduleValidation() {
        setUp();
        admin.createProduct(input("DAD501"));
        ProductDetail d = admin.replaceSchedule("DAD501", new ScheduleInput(
                List.of(rule("2027-03-01", "2027-05-31", "mon", "Wednesday", "SAT")),
                List.of("2027-05-05", "2027-03-10")));
        assertEquals(1, d.schedule().rules().size());
        assertEquals(List.of("MON", "WED", "SAT"), d.schedule().rules().get(0).weekdays());
        assertEquals(List.of("2027-03-10", "2027-05-05"), d.schedule().exclusions());   // sorted
        assertEquals("월·수·토 2027-03-01~2027-05-31", admin.listProducts().get(0).scheduleSummary());

        assertThrows(ValidationException.class, () -> admin.replaceSchedule("DAD501",
                new ScheduleInput(List.of(rule("2027-05-31", "2027-03-01", "WED")), List.of())));      // to < from
        assertThrows(ValidationException.class, () -> admin.replaceSchedule("DAD501",
                new ScheduleInput(List.of(rule("2027-01-01", "2031-01-01", "WED")), List.of())));      // > 1100 days
        assertThrows(ValidationException.class, () -> admin.replaceSchedule("DAD501",
                new ScheduleInput(List.of(rule("2027-03-01", "2027-05-31")), List.of())));             // no weekdays
        assertThrows(ValidationException.class, () -> admin.replaceSchedule("DAD501",
                new ScheduleInput(List.of(rule("2027-03-01", "2027-05-31", "XYZ")), List.of())));
        assertThrows(ValidationException.class, () -> admin.replaceSchedule("DAD501",
                new ScheduleInput(List.of(rule("2027-13-01", "2027-05-31", "WED")), List.of())));      // bad date
        assertThrows(ValidationException.class, () -> admin.replaceSchedule("DAD501",
                new ScheduleInput(List.of(), List.of("not-a-date"))));
        List<RuleInput> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) eleven.add(rule("2027-03-01", "2027-03-31", "WED"));
        assertThrows(ValidationException.class, () -> admin.replaceSchedule("DAD501", new ScheduleInput(eleven, List.of())));
        assertEquals(1, admin.getProduct("DAD501").schedule().rules().size());           // failures changed nothing
    }

    @Test
    @DisplayName("Changing or removing a rule never drops dates that already have bookings")
    public void testScheduleChangesNeverDropBookedDates() {
        setUp();
        createWithWednesdayRule("DAD501");
        LocalDate booked = LocalDate.of(2027, 3, 10);
        store.rows.computeIfAbsent("DAD501", k -> new java.util.TreeMap<>())
                .put(booked, row("DAD501", booked, 30, 4, DepartureRow.Status.OPEN, false));
        assertEquals(13, packages.listPackages(PackageSearch.none(), 0, 100).total());
        admin.replaceSchedule("DAD501", new ScheduleInput(List.of(), List.of()));              // remove every rule
        assertEquals(1, packages.listPackages(PackageSearch.none(), 0, 100).total());          // only the booked date stays
        assertNotNull(packages.getDetail("DAD501", booked));
    }

    @Test
    @DisplayName("Extras: tours, shopping, remark and conditions are validated and can be cleared")
    public void testExtrasValidationAndClearing() {
        setUp();
        admin.createProduct(input("DAD501"));
        RemarkInput remark = new RemarkInput("가격", "포함", "불포함", "포인트", "예약", "객실", "여행", "FAQ");
        ConditionInput cond = new ConditionInput("일반석", false, "한국어 가이드", true, 1, false, true, "usd",
                60, 40, 0, 250000, 6, "약관");
        ProductDetail d = admin.replaceExtras("DAD501", new ExtrasInput(
                List.of(new OptionalTourInput("야간 투어", "호이안", null, "요약", null, "usd", 25.0, 20.0, 4, 60, false, true)),
                List.of(new ShoppingInput("침향", "센터", 90, true)), remark, cond));
        assertEquals(1, d.extras().optionalTours().size());
        assertEquals("USD", d.extras().optionalTours().get(0).currency());              // normalized
        assertEquals("USD", d.extras().condition().localExpensesCurrency());
        assertEquals("포함", d.extras().remark().inclusions());

        assertThrows(ValidationException.class, () -> admin.replaceExtras("DAD501", new ExtrasInput(
                List.of(new OptionalTourInput(" ", null, null, null, null, null, null, null, null, null, null, null)),
                List.of(), null, null)));                                                         // tour needs a name
        assertThrows(ValidationException.class, () -> admin.replaceExtras("DAD501", new ExtrasInput(
                List.of(new OptionalTourInput("t", null, null, null, null, "US", null, null, null, null, null, null)),
                List.of(), null, null)));                                                         // currency
        assertThrows(ValidationException.class, () -> admin.replaceExtras("DAD501", new ExtrasInput(
                List.of(new OptionalTourInput("t", null, null, null, null, null, -1.0, null, null, null, null, null)),
                List.of(), null, null)));                                                         // negative price
        assertThrows(ValidationException.class, () -> admin.replaceExtras("DAD501", new ExtrasInput(
                List.of(), List.of(new ShoppingInput("", null, null, null)), null, null)));
        assertEquals(1, admin.getProduct("DAD501").extras().optionalTours().size());      // failures changed nothing

        ProductDetail cleared = admin.replaceExtras("DAD501", new ExtrasInput(List.of(), List.of(), null, null));
        assertEquals(0, cleared.extras().optionalTours().size());
        assertNull(cleared.extras().remark());
        assertNull(cleared.extras().condition());
    }

    @Test
    @DisplayName("Departure calendar: rule dates, stored/pinned flags, canceled dates stay listed")
    public void testDepartureCalendarIncludesRuleStoredAndCanceledDates() {
        setUp();
        createWithWednesdayRule("DAD501");
        LocalDate stored = LocalDate.of(2027, 3, 10);
        LocalDate canceled = LocalDate.of(2027, 3, 17);
        LocalDate extra = LocalDate.of(2027, 3, 12);                    // a Friday: pinned extra date
        store.rows.computeIfAbsent("DAD501", k -> new java.util.TreeMap<>());
        store.rows.get("DAD501").put(stored, row("DAD501", stored, 30, 12, DepartureRow.Status.OPEN, false));
        store.rows.get("DAD501").put(canceled, row("DAD501", canceled, 30, 0, DepartureRow.Status.CANCELED, true));
        store.rows.get("DAD501").put(extra, row("DAD501", extra, 30, 0, DepartureRow.Status.OPEN, true));

        List<DepartureItem> items = admin.departures("DAD501", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31));
        // Wednesdays 3,10,17(canceled),24,31 + the extra Friday 12
        assertEquals(6, items.size());
        assertEquals("2027-03-03", items.get(0).date());
        DepartureItem s = items.stream().filter(i -> i.date().equals("2027-03-10")).findFirst().orElseThrow();
        assertTrue(s.stored());
        assertTrue(s.fromRule());
        assertEquals(12, s.reservedSeats());
        assertEquals(18, s.remainingSeats());
        assertEquals("WED", s.weekday());
        DepartureItem c = items.stream().filter(i -> i.date().equals("2027-03-17")).findFirst().orElseThrow();
        assertEquals("CANCELED", c.status());
        DepartureItem e = items.stream().filter(i -> i.date().equals("2027-03-12")).findFirst().orElseThrow();
        assertFalse(e.fromRule());
        assertTrue(e.pinned());
        DepartureItem virtual = items.get(0);
        assertFalse(virtual.stored());
        assertEquals(30, virtual.capacity());
    }

    @Test
    @DisplayName("Close/reopen create a pinned row; capacity cannot drop below the reserved seats")
    public void testCloseReopenAndCapacity() {
        setUp();
        createWithWednesdayRule("DAD501");
        LocalDate d = LocalDate.of(2027, 3, 24);
        assertFalse(store.rows.containsKey("DAD501"));                    // nothing stored yet
        DepartureItem closed = admin.closeDeparture("DAD501", d);
        assertEquals("CLOSED", closed.status());
        assertTrue(closed.stored() && closed.pinned());
        assertEquals(30, closed.capacity());                              // default capacity snapshot
        assertEquals("OPEN", admin.reopenDeparture("DAD501", d).status());
        assertThrows(OperationNotAllowedException.class, () -> admin.reopenDeparture("DAD501", d));   // already open

        DepartureItem bigger = admin.setCapacity("DAD501", d, 45);
        assertEquals(45, bigger.capacity());
        store.rows.get("DAD501").put(d, row("DAD501", d, 45, 20, DepartureRow.Status.OPEN, true));    // 20 reserved
        assertThrows(OperationNotAllowedException.class, () -> admin.setCapacity("DAD501", d, 19));
        assertEquals(45, admin.getProduct("DAD501") != null ? store.rows.get("DAD501").get(d).capacity() : 0);
        assertEquals(20, admin.setCapacity("DAD501", d, 20).capacity());                              // equal is fine
        assertThrows(ValidationException.class, () -> admin.setCapacity("DAD501", d, 0));
        assertThrows(ValidationException.class, () -> admin.setCapacity("DAD501", d, 1000));
        assertThrows(ValidationException.class, () -> admin.setCapacity("DAD501", d, null));
    }

    @Test
    @DisplayName("Cancel departure: only without bookings; it leaves the public list and can be reopened")
    public void testCancelDepartureNeedsNoBookingsAndCanBeReopened() {
        setUp();
        createWithWednesdayRule("DAD501");
        LocalDate booked = LocalDate.of(2027, 3, 10);
        LocalDate free = LocalDate.of(2027, 3, 17);
        store.rows.computeIfAbsent("DAD501", k -> new java.util.TreeMap<>())
                .put(booked, row("DAD501", booked, 30, 3, DepartureRow.Status.OPEN, false));
        assertThrows(OperationNotAllowedException.class, () -> admin.cancelDeparture("DAD501", booked));
        assertEquals("OPEN", admin.departures("DAD501", booked, booked).get(0).status());   // unchanged

        int before = packages.listPackages(PackageSearch.none(), 0, 100).items().size();
        assertEquals("CANCELED", admin.cancelDeparture("DAD501", free).status());
        assertEquals(before - 1, packages.listPackages(PackageSearch.none(), 0, 100).items().size());
        assertThrows(PackageNotFoundException.class, () -> packages.getDetail("DAD501", free));
        // the admin calendar still shows it so it can be reopened
        assertEquals("CANCELED", admin.departures("DAD501", free, free).get(0).status());
        assertEquals("OPEN", admin.reopenDeparture("DAD501", free).status());
        assertEquals(before, packages.listPackages(PackageSearch.none(), 0, 100).items().size());
    }

    @Test
    @DisplayName("Extra departure outside the rules: after today, pinned, idempotent, not for canceled dates")
    public void testAddExtraDeparture() {
        setUp();
        createWithWednesdayRule("DAD501");
        LocalDate friday = LocalDate.of(2027, 3, 12);
        DepartureItem item = admin.addDeparture("DAD501", friday);
        assertTrue(item.pinned() && item.stored());
        assertFalse(item.fromRule());
        assertEquals("OPEN", item.status());
        assertNotNull(packages.getDetail("DAD501", friday));                            // now a real departure
        assertEquals(item.date(), admin.addDeparture("DAD501", friday).date());         // idempotent
        assertThrows(ValidationException.class, () -> admin.addDeparture("DAD501", LocalDate.of(2027, 1, 4)));    // today
        assertThrows(ValidationException.class, () -> admin.addDeparture("DAD501", LocalDate.of(2026, 1, 1)));
        assertThrows(ValidationException.class, () -> admin.addDeparture("DAD501", LocalDate.of(2031, 1, 1)));   // too far
        assertThrows(ValidationException.class, () -> admin.addDeparture("DAD501", null));
        admin.cancelDeparture("DAD501", friday);
        assertThrows(OperationNotAllowedException.class, () -> admin.addDeparture("DAD501", friday));
    }

    @Test
    @DisplayName("Departure actions on dates that are not departures are 404")
    public void testDepartureActionsRejectNonDepartureDates() {
        setUp();
        createWithWednesdayRule("DAD501");
        LocalDate thursday = LocalDate.of(2027, 3, 11);
        assertThrows(PackageNotFoundException.class, () -> admin.closeDeparture("DAD501", thursday));
        assertThrows(PackageNotFoundException.class, () -> admin.setCapacity("DAD501", thursday, 10));
        assertThrows(PackageNotFoundException.class, () -> admin.cancelDeparture("DAD501", thursday));
        assertThrows(PackageNotFoundException.class, () -> admin.closeDeparture("NOPE", LocalDate.of(2027, 3, 10)));
        assertThrows(OperationNotAllowedException.class, () -> admin.reopenDeparture("DAD501", thursday));
        assertFalse(store.rows.containsKey("DAD501"));                                  // failures stored nothing
    }
}
