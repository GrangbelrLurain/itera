package aerocore.backend;

import aerocore.backend.dto.AdminDtos.BookingListResponse;
import aerocore.backend.dto.AdminDtos.Dashboard;
import aerocore.backend.dto.PackageBookingDtos.Contact;
import aerocore.backend.dto.PackageBookingDtos.CreateRequest;
import aerocore.backend.dto.PackageBookingDtos.TravelerInput;
import aerocore.backend.exception.UnauthorizedException;
import aerocore.backend.model.BookingSearch;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.PackageBookingData.Status;
import aerocore.backend.repository.InMemoryPackageBookingRepository;
import aerocore.backend.service.AdminAuthService;
import aerocore.backend.service.AdminService;
import aerocore.backend.service.CostBasedPriceCalculator;
import aerocore.backend.service.PackageBookingService;
import aerocore.backend.service.PackageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static aerocore.backend.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Admin auth tokens and the admin read models (booking search, dashboard, expiry sweep). */
public class AdminServiceTest {

    private static final LocalDate WED = LocalDate.of(2027, 3, 10);

    private MutableClock clock;
    private InMemoryPackageBookingRepository bookings;
    private PackageBookingService bookingService;
    private AdminService admin;

    private void setUp() {
        clock = new MutableClock(TODAY);
        List<aerocore.backend.model.ProductData.Rule> wed =
                List.of(rule(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 31), DayOfWeek.WEDNESDAY));
        FakeProducts products = new FakeProducts()
                .add(data(product("DAD501", "다낭 4박5일", 30), wed))
                // few seats left on 03-17: low-seat candidate
                .add(data(product("TYO201", "도쿄 3박4일", 30), wed),
                        row("TYO201", LocalDate.of(2027, 3, 17), 30, 27, DepartureRow.Status.OPEN, false))
                // closed with few seats left: must not show up as low-seat
                .add(data(product("BKK501", "방콕 마감", 30), wed),
                        row("BKK501", LocalDate.of(2027, 3, 17), 30, 29, DepartureRow.Status.CLOSED, true));
        bookings = new InMemoryPackageBookingRepository();
        bookingService = new PackageBookingService(products, bookings, clock);
        admin = new AdminService(bookingService, bookings,
                new PackageService(products, new CostBasedPriceCalculator(), clock), clock);
    }

    private String book(String productCode, String date, String contactName, String phone) {
        return bookingService.create(new CreateRequest(productCode, date, null, new Contact(contactName, phone, null),
                List.of(new TravelerInput("ADULT", "여행자", "1990-01-01", "M")))).bookingCode();
    }

    private String book(String productCode, String contactName, String phone) {
        return book(productCode, "2027-03-10", contactName, phone);
    }

    public static void main(String[] args) {
        AdminServiceTest t = new AdminServiceTest();
        t.testLoginAndTokenVerification();
        t.testTokenTamperingAndExpiry();
        t.testBookingSearchFiltersAndPaging();
        t.testDashboardCountsAndLowSeats();
        t.testListSweepsOverdueBookings();
        System.out.println("✔ AdminServiceTest all assertions passed!");
    }

    @Test
    @DisplayName("Login accepts the configured credentials only and issues a verifiable token")
    public void testLoginAndTokenVerification() {
        setUp();
        AdminAuthService auth = new AdminAuthService("admin", "s3cret!", "secret", clock);
        assertThrows(UnauthorizedException.class, () -> auth.login("admin", "wrong"));
        assertThrows(UnauthorizedException.class, () -> auth.login("root", "s3cret!"));
        assertThrows(UnauthorizedException.class, () -> auth.login(null, null));
        AdminAuthService.Login login = auth.login("admin", "s3cret!");
        assertEquals("admin", auth.verify(login.token()));
        assertEquals("admin", auth.verifyAuthorizationHeader("Bearer " + login.token()));
        assertThrows(UnauthorizedException.class, () -> auth.verifyAuthorizationHeader(null));
        assertThrows(UnauthorizedException.class, () -> auth.verifyAuthorizationHeader("Basic abc"));
    }

    @Test
    @DisplayName("Tampered, foreign-secret and expired tokens are rejected")
    public void testTokenTamperingAndExpiry() {
        setUp();
        AdminAuthService auth = new AdminAuthService("admin", "pw", "secret-A", clock);
        AdminAuthService other = new AdminAuthService("admin", "pw", "secret-B", clock);
        String token = auth.login("admin", "pw").token();
        assertThrows(UnauthorizedException.class, () -> other.verify(token));           // different secret
        assertThrows(UnauthorizedException.class, () -> auth.verify(token + "x"));       // bad signature
        assertThrows(UnauthorizedException.class, () -> auth.verify("garbage"));
        assertThrows(UnauthorizedException.class, () -> auth.verify(""));
        String[] parts = token.split("\\.");
        String forgedBody = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("admin:9999999999".getBytes());
        assertThrows(UnauthorizedException.class, () -> auth.verify(forgedBody + "." + parts[1])); // body swapped
        clock.set("2027-01-04T07:59:59Z");
        assertEquals("admin", auth.verify(token));
        clock.set("2027-01-04T08:00:00Z"); // 8h TTL
        assertThrows(UnauthorizedException.class, () -> auth.verify(token));
    }

    @Test
    @DisplayName("Booking search: status, keyword, product, departure, date range, newest first, paging")
    public void testBookingSearchFiltersAndPaging() {
        setUp();
        clock.set("2027-01-04T10:00:00Z");
        String a = book("DAD501", "홍길동", "010-1111-2222");
        clock.set("2027-01-05T10:00:00Z");
        String b = book("DAD501", "김영희", "010-3333-4444");
        bookingService.pay(b, "MOCK");
        clock.set("2027-01-06T10:00:00Z");
        String c = book("TYO201", "2027-03-24", "박철수", "010-5555-6666");
        bookingService.cancel(c, null);
        clock.set("2027-01-06T11:00:00Z");

        BookingListResponse all = admin.searchBookings(BookingSearch.none(), 0, 20);
        assertEquals(3, all.total());
        assertEquals(c, all.items().get(0).bookingCode());   // newest first
        assertEquals(a, all.items().get(2).bookingCode());
        assertEquals("TYO201", all.items().get(0).productCode());
        assertEquals("DIRECT", all.items().get(0).channelCode());

        assertEquals(1, admin.searchBookings(search(Set.of(Status.CONFIRMED), null, null, null, null, null), 0, 20).total());
        assertEquals(2, admin.searchBookings(search(Set.of(Status.CONFIRMED, Status.CANCELLED), null, null, null, null, null), 0, 20).total());
        assertEquals(1, admin.searchBookings(search(null, null, null, "TYO201", null, null), 0, 20).total());
        assertEquals(2, admin.searchBookings(search(null, null, null, "DAD501", WED, null), 0, 20).total());
        assertEquals(0, admin.searchBookings(search(null, null, null, "DAD501", LocalDate.of(2027, 3, 17), null), 0, 20).total());
        assertEquals(1, admin.searchBookings(search(null, null, null, null, null, "김영"), 0, 20).total());
        assertEquals(1, admin.searchBookings(search(null, null, null, null, null, "5555"), 0, 20).total());
        assertEquals(1, admin.searchBookings(search(null, null, null, null, null, a.toLowerCase()), 0, 20).total());
        assertEquals(0, admin.searchBookings(search(null, null, null, null, null, "없는사람"), 0, 20).total());
        assertEquals(2, admin.searchBookings(search(null, LocalDate.of(2027, 1, 5), LocalDate.of(2027, 1, 6), null, null, null), 0, 20).total());

        BookingListResponse page2 = admin.searchBookings(BookingSearch.none(), 1, 2);
        assertEquals(3, page2.total());
        assertEquals(1, page2.items().size());
        assertEquals(1, page2.page());
        assertEquals(2, page2.size());
        assertEquals(100, admin.searchBookings(BookingSearch.none(), 0, 100000).size());   // size is capped
    }

    private static BookingSearch search(Set<Status> statuses, LocalDate from, LocalDate to, String productCode,
                                        LocalDate departureDate, String keyword) {
        return new BookingSearch(statuses == null ? Set.of() : statuses, from, to, productCode, departureDate, keyword);
    }

    @Test
    @DisplayName("Dashboard: status counts, revenue, expiring soon, low-seat open departures")
    public void testDashboardCountsAndLowSeats() {
        setUp();
        String a = book("DAD501", "A", "010-0000-0001");
        String b = book("DAD501", "B", "010-0000-0002");
        book("DAD501", "C", "010-0000-0003");
        bookingService.pay(a, "MOCK");
        bookingService.cancel(b, null);

        Dashboard d = admin.dashboard();
        assertEquals(1L, d.statusCounts().get("CONFIRMED").longValue());
        assertEquals(1L, d.statusCounts().get("CANCELLED").longValue());
        assertEquals(1L, d.statusCounts().get("PENDING_PAYMENT").longValue());
        assertEquals(0L, d.statusCounts().get("EXPIRED").longValue());
        assertEquals(1085000L, d.confirmedRevenue());
        assertEquals(1L, d.pendingExpiringWithin24h());                   // C is due in exactly 24h (inclusive)
        assertEquals(1, d.lowSeatDepartures().size());                    // only the open TYO201 date with 3 seats left
        assertEquals("TYO201", d.lowSeatDepartures().get(0).productCode());
        assertEquals(3, d.lowSeatDepartures().get(0).remainingSeats());
    }

    @Test
    @DisplayName("Listing sweeps unpaid bookings past their window to EXPIRED and frees seats")
    public void testListSweepsOverdueBookings() {
        setUp();
        String code = book("DAD501", "A", "010-0000-0001");
        assertEquals(29, bookings.availableSeats("DAD501", WED, 30));
        clock.set("2027-01-05T00:00:00Z");
        BookingListResponse r = admin.searchBookings(BookingSearch.none(), 0, 20);
        assertEquals("EXPIRED", r.items().get(0).status());
        assertEquals(code, r.items().get(0).bookingCode());
        assertEquals(30, bookings.availableSeats("DAD501", WED, 30));
        assertEquals(1L, admin.dashboard().statusCounts().get("EXPIRED").longValue());
    }
}
