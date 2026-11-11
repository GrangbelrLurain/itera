package aerocore.backend.controller;

import aerocore.backend.dto.BookingDetailResponse;
import aerocore.backend.dto.CancelBookingRequest;
import aerocore.backend.dto.CancelBookingResponse;
import aerocore.backend.dto.CreateBookingRequest;
import aerocore.backend.dto.CreateBookingResponse;
import aerocore.backend.dto.IssueTicketRequest;
import aerocore.backend.dto.IssueTicketResponse;
import aerocore.backend.exception.BookingNotFoundException;
import aerocore.backend.model.BookingRecord;
import aerocore.backend.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Flight booking prototype kept from the first phase. The un-versioned /api/... paths are aliases. */
@RestController
@Tag(name = "Flight bookings")
public class FlightBookingController {

    private final BookingService bookings;

    public FlightBookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping({"/api/v1/bookings", "/api/bookings"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBooking", summary = "Create a booking and lock a seat")
    public CreateBookingResponse createBooking(@RequestBody CreateBookingRequest request) {
        return bookings.createBooking(request);
    }

    @GetMapping({"/api/v1/bookings/{bookingId}", "/api/bookings/{bookingId}"})
    @Operation(operationId = "getBooking", summary = "Booking by id")
    public BookingDetailResponse getBooking(@PathVariable String bookingId) {
        return bookings.getBooking(bookingId);
    }

    @PostMapping({"/api/v1/bookings/{bookingId}/cancel", "/api/bookings/{bookingId}/cancel"})
    @Operation(operationId = "cancelBooking", summary = "Cancel a booking")
    public CancelBookingResponse cancelBooking(@PathVariable String bookingId,
                                               @RequestBody(required = false) CancelBookingRequest request) {
        String reason = request != null ? request.getCancelReason() : "Customer request";
        return bookings.cancelBooking(bookingId, reason);
    }

    @PostMapping({"/api/v1/tickets/issue", "/api/tickets/issue"})
    @Operation(operationId = "issueTicket", summary = "Issue the e-ticket of a paid booking")
    public IssueTicketResponse issueTicket(@RequestBody IssueTicketRequest request) {
        return bookings.issueTicket(request);
    }

    @GetMapping({"/api/v1/tickets/{ticketNumber}/pdf", "/api/v1/tickets/{ticketNumber}/download.pdf",
            "/api/tickets/{ticketNumber}/pdf", "/api/tickets/{ticketNumber}/download.pdf"})
    @Operation(operationId = "downloadTicketPdf", summary = "E-ticket PDF")
    public ResponseEntity<byte[]> ticketPdf(@PathVariable String ticketNumber) {
        for (BookingRecord record : bookings.getAllBookings()) {
            if (ticketNumber.equalsIgnoreCase(record.getTicketNumber())) {
                byte[] pdf = bookings.getPdfTicketGenerator().generateTicketPdfBytes(
                        record.getTicketNumber(), record.getPnrCode(), record.getPassengerName(),
                        record.getPassengerPassportNumber(), record.getFlightNumber(), record.getDepartureDate(),
                        record.getSeatNumber());
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ticket-" + ticketNumber + ".pdf\"")
                        .body(pdf);
            }
        }
        throw new BookingNotFoundException("Ticket not found with number: " + ticketNumber);
    }
}
