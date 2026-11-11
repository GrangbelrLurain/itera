package aerocore.backend;

import aerocore.backend.dto.*;
import aerocore.backend.exception.BookingNotFoundException;
import aerocore.backend.exception.InvalidTransitionException;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.model.BookingRecord;
import aerocore.backend.model.BookingStatus;
import aerocore.backend.service.BookingService;
import aerocore.backend.service.PdfTicketGenerator;
import aerocore.backend.service.SeatLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class BookingServiceTest {

    private BookingService bookingService;
    private SeatLockManager seatLockManager;
    private PdfTicketGenerator pdfTicketGenerator;

    @BeforeEach
    public void setUp() {
        seatLockManager = new SeatLockManager();
        pdfTicketGenerator = new PdfTicketGenerator();
        bookingService = new BookingService(seatLockManager, pdfTicketGenerator);
    }

    public static void main(String[] args) {
        BookingServiceTest test = new BookingServiceTest();
        test.setUp();
        test.testCreateBookingSuccess();
        test.setUp();
        test.testCreateBookingValidation();
        test.setUp();
        test.testGetBookingSuccess();
        test.setUp();
        test.testGetBookingNotFound();
        test.setUp();
        test.testIssueTicketSuccess();
        test.setUp();
        test.testDoubleTicketingPrevention();
        test.setUp();
        test.testIssueOnCancelledBooking();
        test.setUp();
        test.testCancelFromSeatLockedZeroPenalty();
        test.setUp();
        test.testCancelFromIssuedTwentyPercentPenalty();
        test.setUp();
        test.testCancelAlreadyCancelledBooking();
        System.out.println("✔ BookingServiceTest all assertions passed!");
    }

    @Test
    @DisplayName("Create booking: locks seat, assigns PNR and SEAT_LOCKED status")
    public void testCreateBookingSuccess() {
        CreateBookingRequest req = new CreateBookingRequest(
                "KE701", "2026-11-20", "HONG GILDONG", "M12345678", 850000L);

        CreateBookingResponse resp = bookingService.createBooking(req);

        assertNotNull(resp.getBookingId());
        assertTrue(resp.getBookingId().startsWith("BK-"));
        assertNotNull(resp.getPnrCode());
        assertEquals(6, resp.getPnrCode().length());
        assertTrue(resp.getPnrCode().startsWith("AC"));
        assertEquals("SEAT_LOCKED", resp.getStatus());
        assertTrue(resp.getPaymentDueTimestamp() > System.currentTimeMillis() / 1000);

        // Verify seat was locked in seat lock manager
        assertEquals(1, seatLockManager.getActiveLockCount());
    }

    @Test
    @DisplayName("Create booking input validation errors")
    public void testCreateBookingValidation() {
        assertThrows(ValidationException.class, () ->
                bookingService.createBooking(new CreateBookingRequest("", "2026-11-20", "NAME", "M123", 100000L)));

        assertThrows(ValidationException.class, () ->
                bookingService.createBooking(new CreateBookingRequest("KE701", "2026-11-20", "", "M123", 100000L)));
    }

    @Test
    @DisplayName("Get booking details returns full persisted record")
    public void testGetBookingSuccess() {
        CreateBookingRequest req = new CreateBookingRequest(
                "OZ101", "2026-12-01", "LEE SOONSHIN", "M99999999", 1200000L);
        CreateBookingResponse createResp = bookingService.createBooking(req);

        BookingDetailResponse detail = bookingService.getBooking(createResp.getBookingId());

        assertEquals(createResp.getBookingId(), detail.getBookingId());
        assertEquals(createResp.getPnrCode(), detail.getPnrCode());
        assertEquals("OZ101", detail.getFlightNumber());
        assertEquals("2026-12-01", detail.getDepartureDate());
        assertEquals("LEE SOONSHIN", detail.getPassengerName());
        assertEquals("M99999999", detail.getPassengerPassportNumber());
        assertEquals(1200000L, detail.getTotalAmount());
        assertEquals("SEAT_LOCKED", detail.getStatus());
        assertEquals("1A", detail.getSeatNumber());
    }

    @Test
    @DisplayName("Get booking throws 404 BookingNotFoundException for non-existent ID")
    public void testGetBookingNotFound() {
        assertThrows(BookingNotFoundException.class, () ->
                bookingService.getBooking("BK-NON-EXISTENT"));
    }

    @Test
    @DisplayName("Issue ticket: transitions to ISSUED, generates 724-XXXXXXXXXX and valid PDF binary")
    public void testIssueTicketSuccess() {
        CreateBookingRequest req = new CreateBookingRequest(
                "KE701", "2026-11-20", "PARK JISUNG", "M77777777", 950000L);
        CreateBookingResponse createResp = bookingService.createBooking(req);

        IssueTicketRequest issueReq = new IssueTicketRequest(createResp.getBookingId(), "TX-PAY-20261109-01");
        IssueTicketResponse issueResp = bookingService.issueTicket(issueReq);

        assertEquals(createResp.getBookingId(), issueResp.getBookingId());
        assertNotNull(issueResp.getTicketNumber());
        assertTrue(issueResp.getTicketNumber().startsWith("724-"), "Ticket number must start with 724-");
        assertEquals("ISSUED", issueResp.getStatus());

        // Validate PDF binary payload
        assertNotNull(issueResp.getPdfDownloadUrl(), "PDF download payload must not be null");
        byte[] pdfBytes = Base64.getDecoder().decode(issueResp.getPdfDownloadUrl());
        String pdfStr = new String(pdfBytes, StandardCharsets.US_ASCII);
        assertTrue(pdfStr.startsWith("%PDF-1.4"), "PDF must start with %PDF-1.4");
        assertTrue(pdfStr.contains(issueResp.getTicketNumber()), "PDF must include ticket number");

        // Verify status updated in store
        BookingDetailResponse detail = bookingService.getBooking(createResp.getBookingId());
        assertEquals("ISSUED", detail.getStatus());
        assertEquals(issueResp.getTicketNumber(), detail.getTicketNumber());
    }

    @Test
    @DisplayName("Prevent double ticketing: cannot issue already issued ticket")
    public void testDoubleTicketingPrevention() {
        CreateBookingRequest req = new CreateBookingRequest(
                "KE701", "2026-11-20", "SON HEUNGMIN", "M11111111", 1500000L);
        CreateBookingResponse createResp = bookingService.createBooking(req);

        IssueTicketRequest issueReq = new IssueTicketRequest(createResp.getBookingId(), "TX-PAY-01");
        bookingService.issueTicket(issueReq);

        // Attempting to issue again must fail
        assertThrows(InvalidTransitionException.class, () ->
                bookingService.issueTicket(new IssueTicketRequest(createResp.getBookingId(), "TX-PAY-02")));
    }

    @Test
    @DisplayName("Cannot issue ticket for cancelled booking (terminal state)")
    public void testIssueOnCancelledBooking() {
        CreateBookingRequest req = new CreateBookingRequest(
                "KE701", "2026-11-20", "KIM MINJAE", "M22222222", 800000L);
        CreateBookingResponse createResp = bookingService.createBooking(req);

        bookingService.cancelBooking(createResp.getBookingId(), "Changed mind");

        assertThrows(InvalidTransitionException.class, () ->
                bookingService.issueTicket(new IssueTicketRequest(createResp.getBookingId(), "TX-PAY-03")));
    }

    @Test
    @DisplayName("Cancel from SEAT_LOCKED: 0% penalty fee, 100% refund, releases seat lock")
    public void testCancelFromSeatLockedZeroPenalty() {
        CreateBookingRequest req = new CreateBookingRequest(
                "KE701", "2026-11-20", "CHO GUESUNG", "M33333333", 800000L);
        CreateBookingResponse createResp = bookingService.createBooking(req);
        assertEquals(1, seatLockManager.getActiveLockCount());

        CancelBookingResponse cancelResp = bookingService.cancelBooking(createResp.getBookingId(), "Travel plan cancelled");

        assertEquals(createResp.getBookingId(), cancelResp.getBookingId());
        assertEquals("CANCELLED", cancelResp.getStatus());
        assertEquals(0L, cancelResp.getPenaltyFee(), "Penalty must be 0 before ticketing");
        assertEquals(800000L, cancelResp.getRefundAmount(), "Refund must be 100% before ticketing");

        // Seat lock must be released
        assertEquals(0, seatLockManager.getActiveLockCount(), "Seat lock must be released upon cancellation");
    }

    @Test
    @DisplayName("Cancel from ISSUED: 20% penalty fee, 80% refund, releases seat lock")
    public void testCancelFromIssuedTwentyPercentPenalty() {
        CreateBookingRequest req = new CreateBookingRequest(
                "KE701", "2026-11-20", "HWANG HEECHAN", "M44444444", 1000000L);
        CreateBookingResponse createResp = bookingService.createBooking(req);
        bookingService.issueTicket(new IssueTicketRequest(createResp.getBookingId(), "TX-PAY-04"));

        CancelBookingResponse cancelResp = bookingService.cancelBooking(createResp.getBookingId(), "Emergency refund");

        assertEquals(createResp.getBookingId(), cancelResp.getBookingId());
        assertEquals("CANCELLED", cancelResp.getStatus());
        assertEquals(200000L, cancelResp.getPenaltyFee(), "Penalty must be 20% for issued ticket");
        assertEquals(800000L, cancelResp.getRefundAmount(), "Refund must be 80% for issued ticket");

        // Seat lock must be released
        assertEquals(0, seatLockManager.getActiveLockCount(), "Seat lock must be released upon cancellation");
    }

    @Test
    @DisplayName("Cannot cancel already cancelled booking")
    public void testCancelAlreadyCancelledBooking() {
        CreateBookingRequest req = new CreateBookingRequest(
                "KE701", "2026-11-20", "KIM SEUNGGYU", "M55555555", 700000L);
        CreateBookingResponse createResp = bookingService.createBooking(req);

        bookingService.cancelBooking(createResp.getBookingId(), "First cancel");

        assertThrows(InvalidTransitionException.class, () ->
                bookingService.cancelBooking(createResp.getBookingId(), "Second cancel"));
    }
}
