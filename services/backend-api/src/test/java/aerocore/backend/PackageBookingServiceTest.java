package aerocore.backend;

import aerocore.backend.dto.PackageBookingDtos.Contact;
import aerocore.backend.dto.PackageBookingDtos.CreateRequest;
import aerocore.backend.dto.PackageBookingDtos.Response;
import aerocore.backend.dto.PackageBookingDtos.TravelerInput;
import aerocore.backend.exception.InsufficientSeatsException;
import aerocore.backend.exception.PackageBookingNotFoundException;
import aerocore.backend.exception.PackageNotBookableException;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.PackageBookingData;
import aerocore.backend.repository.InMemoryPackageBookingRepository;
import aerocore.backend.service.PackageBookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static aerocore.backend.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Booking lifecycle on virtual departures (no database): validation, pricing snapshot, lazy departure rows,
 * seats, pay, cancel, expiry.
 */
public class PackageBookingServiceTest {

    static final LocalDate DEPARTURE = LocalDate.of(2027, 3, 10);   // Wednesday, inside the rule
    private static final String DATE = "2027-03-10";

    private MutableClock clock;
    private FakeProducts products;
    private InMemoryPackageBookingRepository bookings;
    private PackageBookingService service;
    private int capacity;

    private void setUp(int defaultCapacity, DepartureRow... rows) {
        capacity = defaultCapacity;
        clock = new MutableClock(TODAY);
        products = new FakeProducts().add(
                data(product("DAD501", "다낭 4박5일", defaultCapacity),
                        List.of(rule(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 31),
                                DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))),
                rows);
        bookings = new InMemoryPackageBookingRepository();
        service = new PackageBookingService(products, bookings, clock);
    }

    private void setUpOpen(int seats) {
        setUp(seats);
    }

    private int seatsLeft() {
        return bookings.availableSeats("DAD501", DEPARTURE, capacity);
    }

    private static TravelerInput adult(String name) {
        return new TravelerInput("ADULT", name, "1990-05-01", "M");
    }

    private static CreateRequest request(String date, TravelerInput... travelers) {
        return new CreateRequest("DAD501", date, null,
                new Contact("홍길동", "010-1234-5678", "hong@example.com"), List.of(travelers));
    }

    private static CreateRequest request(TravelerInput... travelers) {
        return request(DATE, travelers);
    }

    public static void main(String[] args) throws Exception {
        PackageBookingServiceTest t = new PackageBookingServiceTest();
        t.testCreateComputesPriceHoldsSeatsAndCreatesDepartureRow();
        t.testPriceSnapshotIsStoredPerTraveler();
        t.testInfantsDoNotTakeSeats();
        t.testSeatsAreTrackedPerDeparture();
        t.testValidationNeverCreatesDepartureRows();
        t.testOnlyValidOpenDeparturesCanBeBooked();
        t.testPinnedDateOutsideRuleCanBeBooked();
        t.testInsufficientSeats();
        t.testConcurrentBookingsNeverOversell();
        t.testPayThenCannotPayAgain();
        t.testCancelPendingReleasesSeatsWithoutPenalty();
        t.testCancelConfirmedAppliesPenaltyByDaysBeforeDeparture();
        t.testCancelTwiceFails();
        t.testPaymentWindowExpiryReleasesSeats();
        t.testUnknownBooking();
        System.out.println("✔ PackageBookingServiceTest all assertions passed!");
    }

    @Test
    @DisplayName("Create: PENDING_PAYMENT, unit prices summed, seats held, departure row created by the first booking")
    public void testCreateComputesPriceHoldsSeatsAndCreatesDepartureRow() {
        setUpOpen(10);
        assertFalse(bookings.hasDepartureRow("DAD501", DEPARTURE));          // virtual before the first booking
        Response r = service.create(request(adult("A"), adult("B"),
                new TravelerInput("NOBED_CHILD", "C", "2018-01-01", "F")));
        assertEquals("PENDING_PAYMENT", r.status());
        assertEquals("DAD501", r.productCode());
        assertEquals(DATE, r.departureDate());
        assertEquals("DIRECT", r.channelCode());
        assertEquals(3, r.seatCount());
        // adult: 1,000,000 - 50,000 + 135,000 = 1,085,000 (x2); no-bed child: 900,000 - 50,000 + 135,000 = 985,000
        assertEquals(1085000L * 2 + 985000L, r.totalAmount());
        assertTrue(bookings.hasDepartureRow("DAD501", DEPARTURE));           // stored rule S1
        assertEquals(7, seatsLeft());
        assertEquals("2027-01-05T00:00:00Z", r.paymentDueAt());
        assertEquals(3, r.travelers().size());
    }

    @Test
    @DisplayName("Each traveler row keeps a price snapshot (no cost for manual prices, commission 0 without a policy)")
    public void testPriceSnapshotIsStoredPerTraveler() {
        setUpOpen(10);
        String code = service.create(request(adult("A"))).bookingCode();
        PackageBookingData.Traveler t = bookings.findByCode(code).orElseThrow().travelers().get(0);
        assertEquals(1085000, t.unitPrice());          // what the customer pays
        assertEquals(1000000, t.unitSalePrice());
        assertEquals(50000, t.unitDiscount());
        assertEquals(135000, t.unitPassThrough());      // fuel + taxes
        assertNull(t.unitCost());                       // manually priced: no cost behind the price
        assertEquals(0L, t.unitCommission().longValue());   // no commission policy for DIRECT
        assertEquals("DIRECT", bookings.findByCode(code).orElseThrow().channelCode());
    }

    @Test
    @DisplayName("Infants are not charged a seat and get no discount")
    public void testInfantsDoNotTakeSeats() {
        setUpOpen(10);
        Response r = service.create(request(adult("A"), new TravelerInput("INFANT", "I", "2026-06-01", "M")));
        assertEquals(1, r.seatCount());
        assertEquals(9, seatsLeft());
        assertEquals(1085000L + 165000L, r.totalAmount());
    }

    @Test
    @DisplayName("Seats belong to one date: another departure of the same product is unaffected")
    public void testSeatsAreTrackedPerDeparture() {
        setUpOpen(10);
        service.create(request(adult("A"), adult("B")));
        assertEquals(8, seatsLeft());
        LocalDate next = LocalDate.of(2027, 3, 17);
        assertEquals(10, bookings.availableSeats("DAD501", next, capacity));
        service.create(request("2027-03-17", adult("C")));
        assertEquals(9, bookings.availableSeats("DAD501", next, capacity));
        assertEquals(8, seatsLeft());
    }

    @Test
    @DisplayName("Validation: bad requests are rejected and never create departure rows or hold seats")
    public void testValidationNeverCreatesDepartureRows() {
        setUpOpen(10);
        assertThrows(ValidationException.class, () -> service.create(null));
        assertThrows(ValidationException.class, () -> service.create(
                new CreateRequest(null, DATE, null, new Contact("홍길동", "010-1234-5678", null), List.of(adult("A")))));
        assertThrows(ValidationException.class, () -> service.create(request("not-a-date", adult("A"))));
        assertThrows(ValidationException.class, () -> service.create(request()));
        assertThrows(ValidationException.class, () -> service.create(
                request(new TravelerInput("NOBED_CHILD", "C", "2018-01-01", "F"))));              // no adult
        assertThrows(ValidationException.class, () -> service.create(
                request(adult("A"), new TravelerInput("INFANT", "I1", "2026-06-01", "M"),
                        new TravelerInput("INFANT", "I2", "2026-06-02", "F"))));                   // infants > adults
        assertThrows(ValidationException.class, () -> service.create(
                request(new TravelerInput("ADULT", "A", "1990-05-01", "X"))));                   // gender
        assertThrows(ValidationException.class, () -> service.create(
                request(new TravelerInput("ADULT", "A", "not-a-date", "M"))));                   // birth date format
        assertThrows(ValidationException.class, () -> service.create(
                request(new TravelerInput("ADULT", "A", "2030-01-01", "M"))));                   // future birth date
        assertThrows(ValidationException.class, () -> service.create(
                request(adult("A"), new TravelerInput("EXTRABED_CHILD", "C", "2018-01-01", "F")))); // not sold
        assertThrows(ValidationException.class, () -> service.create(
                new CreateRequest("DAD501", DATE, null, new Contact("홍길동", "abc", null), List.of(adult("A")))));
        assertThrows(ValidationException.class, () -> service.create(
                new CreateRequest("DAD501", DATE, null, new Contact("홍길동", "010-1234-5678", "bad"), List.of(adult("A")))));
        assertThrows(ValidationException.class, () -> service.create(
                new CreateRequest("DAD501", DATE, "bad channel!", new Contact("홍길동", "010-1234-5678", null),
                        List.of(adult("A")))));
        List<TravelerInput> tooMany = new ArrayList<>();
        for (int i = 0; i < 10; i++) tooMany.add(adult("P" + i));
        assertThrows(ValidationException.class, () -> service.create(
                new CreateRequest("DAD501", DATE, null, new Contact("홍길동", "010-1234-5678", null), tooMany)));
        assertFalse(bookings.hasDepartureRow("DAD501", DEPARTURE));
        assertEquals(10, seatsLeft());
        // a valid channel code is normalized
        Response r = service.create(new CreateRequest("DAD501", DATE, "agency_a",
                new Contact("홍길동", "010-1234-5678", null), List.of(adult("A"))));
        assertEquals("AGENCY_A", r.channelCode());
    }

    @Test
    @DisplayName("Only valid, open departures can be booked; failures create no departure row")
    public void testOnlyValidOpenDeparturesCanBeBooked() {
        setUp(10, row("DAD501", LocalDate.of(2027, 3, 13), 10, 0, DepartureRow.Status.CLOSED, true));
        assertThrows(PackageNotFoundException.class, () -> service.create(
                new CreateRequest("NOPE", DATE, null, new Contact("홍길동", "010-1234-5678", null), List.of(adult("A")))));
        assertThrows(PackageNotBookableException.class, () -> service.create(request("2027-03-11", adult("A")))); // Thursday: no rule
        assertThrows(PackageNotBookableException.class, () -> service.create(request("2027-06-02", adult("A")))); // after the rule period
        assertThrows(PackageNotBookableException.class, () -> service.create(request("2027-03-13", adult("A")))); // closed by an admin
        assertFalse(bookings.hasDepartureRow("DAD501", LocalDate.of(2027, 3, 11)));

        // canceled departure
        setUp(10, row("DAD501", DEPARTURE, 10, 0, DepartureRow.Status.CANCELED, true));
        assertThrows(PackageNotBookableException.class, () -> service.create(request(adult("A"))));

        // inactive product
        setUp(10);
        products.add(data(product("DAD501", "다낭", 10, false, 0),
                List.of(rule(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 31), DayOfWeek.WEDNESDAY))));
        assertThrows(PackageNotBookableException.class, () -> service.create(request(adult("A"))));

        // departure date today or in the past
        setUp(10);
        clock.set("2027-03-10T00:00:00Z");
        assertThrows(PackageNotBookableException.class, () -> service.create(request(adult("A"))));
        assertFalse(bookings.hasDepartureRow("DAD501", DEPARTURE));
    }

    @Test
    @DisplayName("A date outside the rule can be booked when an admin pinned it")
    public void testPinnedDateOutsideRuleCanBeBooked() {
        LocalDate friday = LocalDate.of(2027, 3, 12);                  // the rule only has Wed + Sat
        setUp(10, row("DAD501", friday, 10, 0, DepartureRow.Status.OPEN, true));
        Response r = service.create(request("2027-03-12", adult("A")));
        assertEquals("2027-03-12", r.departureDate());
        assertEquals(9, bookings.availableSeats("DAD501", friday, 10));
    }

    @Test
    @DisplayName("Not enough seats -> InsufficientSeatsException, no departure row, nothing stored")
    public void testInsufficientSeats() {
        setUpOpen(1);
        assertThrows(InsufficientSeatsException.class, () -> service.create(request(adult("A"), adult("B"))));
        assertFalse(bookings.hasDepartureRow("DAD501", DEPARTURE));        // the lazy row was rolled back
        assertEquals(1, seatsLeft());
        assertNotNull(service.create(request(adult("A"))));                  // the last seat can still be booked
        assertEquals(0, seatsLeft());
        assertThrows(InsufficientSeatsException.class, () -> service.create(request(adult("B"))));
    }

    @Test
    @DisplayName("Concurrent first bookings never oversell the departure")
    public void testConcurrentBookingsNeverOversell() throws Exception {
        setUpOpen(5);
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.create(request(adult("A")));
                    ok.incrementAndGet();
                } catch (InsufficientSeatsException e) {
                    full.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(5, ok.get());
        assertEquals(15, full.get());
        assertEquals(0, seatsLeft());
    }

    @Test
    @DisplayName("Pay moves PENDING_PAYMENT to CONFIRMED once")
    public void testPayThenCannotPayAgain() {
        setUpOpen(10);
        String code = service.create(request(adult("A"))).bookingCode();
        assertThrows(ValidationException.class, () -> service.pay(code, " "));
        Response paid = service.pay(code, "MOCK-1");
        assertEquals("CONFIRMED", paid.status());
        assertEquals("MOCK-1", paid.paymentReference());
        assertNotNull(paid.confirmedAt());
        assertThrows(PackageNotBookableException.class, () -> service.pay(code, "MOCK-2"));
        assertEquals("CONFIRMED", service.get(code).status());
    }

    @Test
    @DisplayName("Cancelling an unpaid booking: no penalty/refund, seats released")
    public void testCancelPendingReleasesSeatsWithoutPenalty() {
        setUpOpen(10);
        String code = service.create(request(adult("A"), adult("B"))).bookingCode();
        assertEquals(8, seatsLeft());
        Response c = service.cancel(code, "changed my mind");
        assertEquals("CANCELLED", c.status());
        assertEquals(0, c.penaltyAmount());
        assertEquals(0, c.refundAmount());
        assertEquals("changed my mind", c.cancelReason());
        assertEquals(10, seatsLeft());
    }

    @Test
    @DisplayName("Cancelling a confirmed booking applies the penalty schedule")
    public void testCancelConfirmedAppliesPenaltyByDaysBeforeDeparture() {
        // departure 2027-03-10; total for one adult = 1,085,000
        String[][] cases = {
                {"2027-02-08T09:00:00Z", "0"},       // 30 days before -> 0%
                {"2027-02-18T09:00:00Z", "10"},      // 20 days -> 10%
                {"2027-02-28T09:00:00Z", "20"},      // 10 days -> 20%
                {"2027-03-07T09:00:00Z", "30"},      // 3 days -> 30%
                {"2027-03-09T09:00:00Z", "50"},      // 1 day -> 50%
        };
        for (String[] c : cases) {
            setUpOpen(10);
            String code = service.create(request(adult("A"))).bookingCode();
            service.pay(code, "MOCK");
            clock.set(c[0]);
            Response r = service.cancel(code, null);
            long expectedPenalty = 1085000L * Integer.parseInt(c[1]) / 100;
            assertEquals(expectedPenalty, r.penaltyAmount(), "penalty at " + c[0]);
            assertEquals(1085000L - expectedPenalty, r.refundAmount(), "refund at " + c[0]);
            assertEquals("Customer request", r.cancelReason());
            assertEquals(10, seatsLeft());
        }
    }

    @Test
    @DisplayName("A booking cannot be cancelled twice (seats are not released twice)")
    public void testCancelTwiceFails() {
        setUpOpen(10);
        String code = service.create(request(adult("A"))).bookingCode();
        service.cancel(code, null);
        assertThrows(PackageNotBookableException.class, () -> service.cancel(code, null));
        assertEquals(10, seatsLeft());
    }

    @Test
    @DisplayName("Unpaid bookings expire after the payment window and release their seats")
    public void testPaymentWindowExpiryReleasesSeats() {
        setUpOpen(10);
        String code = service.create(request(adult("A"), adult("B"))).bookingCode();
        clock.set("2027-01-04T23:59:59Z");
        assertEquals("PENDING_PAYMENT", service.get(code).status());
        clock.set("2027-01-05T00:00:00Z");
        assertEquals("EXPIRED", service.get(code).status());
        assertEquals(10, seatsLeft());
        assertThrows(PackageNotBookableException.class, () -> service.pay(code, "MOCK"));
        assertThrows(PackageNotBookableException.class, () -> service.cancel(code, null));
    }

    @Test
    @DisplayName("Unknown booking code raises PackageBookingNotFoundException")
    public void testUnknownBooking() {
        setUpOpen(10);
        assertThrows(PackageBookingNotFoundException.class, () -> service.get("PKNOPE"));
    }
}
