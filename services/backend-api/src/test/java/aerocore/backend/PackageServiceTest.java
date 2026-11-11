package aerocore.backend;

import aerocore.backend.dto.PackageDetailResponse;
import aerocore.backend.dto.PackageSummaryResponse;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.ProductData;
import aerocore.backend.service.CostBasedPriceCalculator;
import aerocore.backend.service.PackageSearch;
import aerocore.backend.service.PackageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static aerocore.backend.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Package list/detail: virtual departure dates, derived values and prices (no database needed). */
public class PackageServiceTest {

    private static final LocalDate WED = LocalDate.of(2027, 3, 10);        // a Wednesday inside the rule
    private static final LocalDate MARCH_1 = LocalDate.of(2027, 3, 1);
    private static final LocalDate MAY_31 = LocalDate.of(2027, 5, 31);

    private MutableClock clock;
    private FakeProducts repo;
    private PackageService service;

    private void setUp(DepartureRow... rows) {
        clock = new MutableClock(TODAY);
        repo = new FakeProducts().add(
                data(product("DAD501", "다낭 4박5일", 30), List.of(rule(MARCH_1, MAY_31, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))),
                rows);
        service = new PackageService(repo, new CostBasedPriceCalculator(), clock);
    }

    private PackageSummaryResponse.Page list(PackageSearch search) {
        return service.listPackages(search, 0, 100);
    }

    public static void main(String[] args) {
        PackageServiceTest t = new PackageServiceTest();
        t.testDetailBasicDerivedFields();
        t.testMeetingTimeOnPreviousDayWhenLaterThanDeparture();
        t.testVirtualDatesComeFromRules();
        t.testRulesExclusionsAndInactiveRules();
        t.testStoredRowsKeepBookedAndPinnedDatesAlive();
        t.testStatusIsDerivedFromStoredRow();
        t.testPricesAndDiscount();
        t.testItineraryGroupedByDayWithDates();
        t.testDetailRejectsDatesThatAreNotDepartures();
        t.testListFiltersOrderAndPaging();
        t.testLowSeatDepartures();
        System.out.println("✔ PackageServiceTest all assertions passed!");
    }

    @Test
    @DisplayName("Detail: name, departure/arrival info, free-cancellation date, default seats for a virtual date")
    public void testDetailBasicDerivedFields() {
        setUp();
        PackageDetailResponse.Basic b = service.getDetail("DAD501", WED).basic();
        assertEquals("◆프리미엄◆다낭 4박5일", b.name());
        assertEquals("ICN-DAD 09:30", b.departureInfo());
        assertEquals("27-03-14 [DAD-ICN 20:10]", b.arrivalInfo());
        assertEquals("2027-02-08", b.freeCancellationUntil());     // departure - 30 days
        assertEquals("OPEN", b.status());
        assertFalse(b.isStoredDeparture());                        // virtual: no row yet
        assertEquals(30, b.capacity());
        assertEquals(0, b.reservedSeats());
        assertEquals(30, b.remainingSeats());
    }

    @Test
    @DisplayName("Meeting time later than the departure time means the day before")
    public void testMeetingTimeOnPreviousDayWhenLaterThanDeparture() {
        setUp();
        assertEquals("07:30", service.getDetail("DAD501", WED).basic().meetingTime());
        repo.products.put("DAD501", new ProductData(
                new ProductData.Product("DAD501", "다낭", "프리미엄", 4, 5, "ICN-DAD", "06:50", "DAD-ICN", "20:10",
                        true, false, "23:00", "공항", "리조트", 5, null, null, 0, 30, true),
                defaultPrices(), repo.products.get("DAD501").rules(), Set.of(), List.of(), List.of(), List.of(),
                null, null));
        assertEquals("2027-03-09 23:00", service.getDetail("DAD501", WED).basic().meetingTime());
    }

    @Test
    @DisplayName("Dates are computed from the rule: valid period and weekdays, after today")
    public void testVirtualDatesComeFromRules() {
        setUp();
        PackageSummaryResponse.Page p = list(PackageSearch.none());
        // Wed + Sat from 2027-03-01 to 2027-05-31: 13 Wednesdays + 13 Saturdays
        assertEquals(26, p.total());
        assertEquals("2027-03-03", p.items().get(0).departureDate());          // first Wednesday
        assertEquals("2027-03-06", p.items().get(1).departureDate());          // first Saturday
        for (PackageSummaryResponse r : p.items()) {
            LocalDate d = LocalDate.parse(r.departureDate());
            assertTrue(d.getDayOfWeek() == DayOfWeek.WEDNESDAY || d.getDayOfWeek() == DayOfWeek.SATURDAY);
            assertFalse(d.isBefore(MARCH_1) || d.isAfter(MAY_31));
        }
    }

    @Test
    @DisplayName("Exclusions and inactive rules remove dates; dates up to today are never offered")
    public void testRulesExclusionsAndInactiveRules() {
        clock = new MutableClock(TODAY);
        ProductData.Product p = product("DAD501", "다낭", 30);
        ProductData.Rule r = rule(MARCH_1, MAY_31, DayOfWeek.WEDNESDAY);
        repo = new FakeProducts().add(data(p, List.of(r), setOf(WED)));
        service = new PackageService(repo, new CostBasedPriceCalculator(), clock);
        assertEquals(12, list(PackageSearch.none()).total());                   // 13 Wednesdays - 1 excluded
        assertThrows(PackageNotFoundException.class, () -> service.getDetail("DAD501", WED));

        repo.add(data(p, List.of(inactive(r))));
        assertEquals(0, list(PackageSearch.none()).total());                    // inactive rule produces nothing

        // today = a Wednesday inside the rule: that day itself is not a departure any more
        repo.add(data(p, List.of(r)));
        clock.set("2027-03-10T00:00:00Z");
        assertThrows(PackageNotFoundException.class, () -> service.getDetail("DAD501", WED));
        assertEquals("2027-03-17", list(PackageSearch.none()).items().get(0).departureDate());
    }

    @Test
    @DisplayName("Stored rows: booked or pinned dates stay valid outside the rule; empty/canceled rows do not")
    public void testStoredRowsKeepBookedAndPinnedDatesAlive() {
        LocalDate thursdayBooked = LocalDate.of(2027, 3, 11);       // not a rule weekday, has bookings
        LocalDate fridayPinned = LocalDate.of(2027, 3, 12);         // not a rule weekday, pinned by an admin
        LocalDate tuesdayEmpty = LocalDate.of(2027, 3, 9);          // stored row without bookings/pin
        LocalDate canceledWed = LocalDate.of(2027, 3, 17);          // rule date, but the departure was canceled
        LocalDate afterRule = LocalDate.of(2027, 6, 5);             // outside the valid period, has bookings
        setUp(row("DAD501", thursdayBooked, 30, 4, DepartureRow.Status.OPEN, false),
                row("DAD501", fridayPinned, 30, 0, DepartureRow.Status.OPEN, true),
                row("DAD501", tuesdayEmpty, 30, 0, DepartureRow.Status.OPEN, false),
                row("DAD501", canceledWed, 30, 0, DepartureRow.Status.CANCELED, true),
                row("DAD501", afterRule, 30, 2, DepartureRow.Status.OPEN, false));
        assertNotNull(service.getDetail("DAD501", thursdayBooked));
        assertNotNull(service.getDetail("DAD501", fridayPinned));
        assertNotNull(service.getDetail("DAD501", afterRule));
        assertThrows(PackageNotFoundException.class, () -> service.getDetail("DAD501", tuesdayEmpty));
        assertThrows(PackageNotFoundException.class, () -> service.getDetail("DAD501", canceledWed));
        // 26 rule dates - 1 canceled + 3 extra stored dates
        assertEquals(28, list(PackageSearch.none()).total());
    }

    @Test
    @DisplayName("Status is derived: stored CLOSED, or FULL when no seats are left, otherwise OPEN")
    public void testStatusIsDerivedFromStoredRow() {
        LocalDate closed = LocalDate.of(2027, 3, 13);
        LocalDate full = LocalDate.of(2027, 3, 17);
        LocalDate few = LocalDate.of(2027, 3, 20);
        setUp(row("DAD501", closed, 30, 30, DepartureRow.Status.CLOSED, true),
                row("DAD501", full, 24, 24, DepartureRow.Status.OPEN, false),
                row("DAD501", few, 20, 18, DepartureRow.Status.OPEN, false));
        assertEquals("CLOSED", service.getDetail("DAD501", closed).basic().status());
        PackageDetailResponse.Basic f = service.getDetail("DAD501", full).basic();
        assertEquals("FULL", f.status());
        assertEquals(0, f.remainingSeats());
        assertTrue(f.isStoredDeparture());
        PackageDetailResponse.Basic o = service.getDetail("DAD501", few).basic();
        assertEquals("OPEN", o.status());
        assertEquals(20, o.capacity());                              // the stored capacity wins over the default 30
        assertEquals(2, o.remainingSeats());
    }

    @Test
    @DisplayName("Prices: payable = (sale - discount) + pass-through; infants get no discount; unsold types flagged")
    public void testPricesAndDiscount() {
        setUp();
        List<PackageDetailResponse.Price> prices = service.getDetail("DAD501", WED).prices();
        PackageDetailResponse.Price adult = prices.get(0);
        assertEquals("ADULT", adult.targetType());
        assertEquals(1000000, adult.salePrice());
        assertEquals(50000, adult.discount());
        assertEquals(1135000, adult.totalPrice());                   // sale + fuel + taxes
        assertEquals(1085000, adult.discountedTotalPrice());
        PackageDetailResponse.Price infant = prices.get(2);
        assertEquals(0, infant.discount());
        assertEquals(165000, infant.totalPrice());
        assertEquals(165000, infant.discountedTotalPrice());
        assertFalse(prices.get(3).isSalesAvailable());               // EXTRABED_CHILD is not sold
    }

    @Test
    @DisplayName("Itinerary rows are grouped by day and dated from the departure date")
    public void testItineraryGroupedByDayWithDates() {
        setUp();
        List<PackageDetailResponse.Day> days = service.getDetail("DAD501", WED).itinerary().days();
        assertEquals(2, days.size());
        assertEquals(1, days.get(0).day());
        assertEquals("2027-03-10", days.get(0).date());
        assertEquals("2027-03-11", days.get(1).date());
        assertEquals("SIGHTSEEING", days.get(1).items().get(0).serviceType());
    }

    @Test
    @DisplayName("Detail is 404 for unknown products, inactive products and non-departure dates")
    public void testDetailRejectsDatesThatAreNotDepartures() {
        setUp();
        assertThrows(PackageNotFoundException.class, () -> service.getDetail("NOPE", WED));
        assertThrows(PackageNotFoundException.class, () -> service.getDetail("DAD501", LocalDate.of(2027, 3, 11))); // Thu
        assertThrows(PackageNotFoundException.class, () -> service.getDetail("DAD501", LocalDate.of(2027, 6, 2)));  // after rule
        repo.add(data(product("DAD501", "다낭", 30, false, 0), List.of(rule(MARCH_1, MAY_31, DayOfWeek.WEDNESDAY))));
        assertThrows(PackageNotFoundException.class, () -> service.getDetail("DAD501", WED));
        assertEquals(0, list(PackageSearch.none()).total());           // inactive products are not listed
    }

    @Test
    @DisplayName("List: date range, keyword, status, weekday filters; ordering across products; paging")
    public void testListFiltersOrderAndPaging() {
        clock = new MutableClock(TODAY);
        repo = new FakeProducts()
                .add(data(product("DAD501", "다낭", 30), List.of(rule(MARCH_1, MAY_31, DayOfWeek.WEDNESDAY))),
                        row("DAD501", WED, 30, 30, DepartureRow.Status.OPEN, false),                 // FULL
                        row("DAD501", LocalDate.of(2027, 3, 17), 30, 0, DepartureRow.Status.CLOSED, true))
                .add(data(product("TYO201", "도쿄", 40), List.of(rule(MARCH_1, MAY_31, DayOfWeek.FRIDAY))));
        service = new PackageService(repo, new CostBasedPriceCalculator(), clock);

        assertEquals(26, list(PackageSearch.none()).total());          // 13 Wednesdays + 13 Fridays
        assertEquals("2027-03-03", list(PackageSearch.none()).items().get(0).departureDate());
        assertEquals("DAD501", list(PackageSearch.none()).items().get(0).productCode());
        assertEquals("TYO201", list(PackageSearch.none()).items().get(1).productCode()); // 03-05 (Fri) after 03-03

        assertEquals(13, list(new PackageSearch(null, null, "도쿄", Set.of(), Set.of())).total());
        assertEquals(13, list(new PackageSearch(null, null, "tyo", Set.of(), Set.of())).total());   // code, case-insensitive
        assertEquals(0, list(new PackageSearch(null, null, "없는", Set.of(), Set.of())).total());
        assertEquals(6, list(new PackageSearch(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 21), null,
                Set.of(), Set.of())).total());                          // Wed 3,10,17 + Fri 5,12,19
        assertEquals(13, list(new PackageSearch(null, null, null, Set.of(), Set.of(DayOfWeek.FRIDAY))).total());
        assertEquals(1, list(new PackageSearch(null, null, null, Set.of("full"), Set.of())).total());
        assertEquals(1, list(new PackageSearch(null, null, null, Set.of("closed"), Set.of())).total());
        assertEquals(24, list(new PackageSearch(null, null, null, Set.of("open"), Set.of())).total());

        PackageSummaryResponse.Page page2 = service.listPackages(PackageSearch.none(), 1, 10);
        assertEquals(26, page2.total());
        assertEquals(10, page2.items().size());
        assertEquals(1, page2.page());
        assertEquals(6, service.listPackages(PackageSearch.none(), 2, 10).items().size());
        assertEquals(100, service.listPackages(PackageSearch.none(), 0, 100000).size());       // page size is capped
        PackageSummaryResponse first = list(PackageSearch.none()).items().get(0);
        assertEquals(1135000, first.adultTotalPrice());                // sale + fuel + taxes
        assertEquals(1085000, first.adultDiscountedTotalPrice());      // after the 50,000 discount
    }

    @Test
    @DisplayName("Low-seat departures: open stored rows with few seats left (not closed, not canceled)")
    public void testLowSeatDepartures() {
        setUp(row("DAD501", WED, 30, 27, DepartureRow.Status.OPEN, false),                        // 3 left
                row("DAD501", LocalDate.of(2027, 3, 13), 30, 30, DepartureRow.Status.OPEN, false), // 0 left (FULL)
                row("DAD501", LocalDate.of(2027, 3, 17), 30, 29, DepartureRow.Status.CLOSED, true), // closed: excluded
                row("DAD501", LocalDate.of(2027, 3, 20), 30, 10, DepartureRow.Status.OPEN, false)); // 20 left
        List<PackageService.LowSeat> low = service.lowSeatDepartures(5);
        assertEquals(2, low.size());
        assertEquals("2027-03-10", low.get(0).departureDate());
        assertEquals(3, low.get(0).remainingSeats());
        assertEquals("2027-03-13", low.get(1).departureDate());
        assertEquals(0, low.get(1).remainingSeats());
    }
}
