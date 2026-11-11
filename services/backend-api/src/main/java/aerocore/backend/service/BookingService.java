package aerocore.backend.service;

import aerocore.backend.dto.*;
import aerocore.backend.exception.BookingNotFoundException;
import aerocore.backend.exception.InvalidTransitionException;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.model.BookingRecord;
import aerocore.backend.model.BookingStatus;
import aerocore.backend.repository.BookingRepository;
import aerocore.backend.repository.InMemoryBookingRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe core business service for AeroCore booking lifecycle and ticketing.
 */
public class BookingService {

    private final ConcurrentHashMap<String, BookingRecord> bookings = new ConcurrentHashMap<>();
    private final SeatLockManager seatLockManager;
    private final PdfTicketGenerator pdfTicketGenerator;
    private final BookingRepository repository;
    private final SecureRandom random = new SecureRandom();

    private static final String PNR_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long DEFAULT_LOCK_DURATION_SECONDS = 1800; // 30 minutes

    public BookingService() {
        this(new SeatLockManager(), new PdfTicketGenerator());
    }

    public BookingService(SeatLockManager seatLockManager, PdfTicketGenerator pdfTicketGenerator) {
        this(seatLockManager, pdfTicketGenerator, new InMemoryBookingRepository());
    }

    /**
     * Write-through service: live records are kept in an identity map (so per-record locking works)
     * and every state change is persisted via the repository. Existing bookings are loaded on startup
     * and their seat locks are restored.
     */
    public BookingService(SeatLockManager seatLockManager, PdfTicketGenerator pdfTicketGenerator,
                          BookingRepository repository) {
        this.seatLockManager = seatLockManager;
        this.pdfTicketGenerator = pdfTicketGenerator;
        this.repository = repository;
        for (BookingRecord r : repository.findAll()) {
            bookings.put(r.getBookingId(), r);
            if (r.getStatus() != BookingStatus.CANCELLED && r.getSeatNumber() != null) {
                seatLockManager.acquireSpecificSeat(
                        r.getFlightNumber(), r.getDepartureDate(), r.getSeatNumber(), r.getBookingId());
            }
        }
    }

    /**
     * POST /api/v1/bookings: Creates a booking, allocates and locks a seat.
     */
    public CreateBookingResponse createBooking(CreateBookingRequest req) {
        if (req == null) {
            throw new ValidationException("Request body cannot be null");
        }
        if (req.getPassengerName() == null || req.getPassengerName().trim().isEmpty()) {
            throw new ValidationException("passengerName is required");
        }
        if (req.getFlightNumber() == null || req.getFlightNumber().trim().isEmpty()) {
            throw new ValidationException("flightNumber is required");
        }
        if (req.getDepartureDate() == null || req.getDepartureDate().trim().isEmpty()) {
            throw new ValidationException("departureDate is required");
        }

        String flight = req.getFlightNumber().trim().toUpperCase();
        String date = req.getDepartureDate().trim();
        String name = req.getPassengerName().trim();
        String passport = req.getPassengerPassportNumber() != null ? req.getPassengerPassportNumber().trim() : "M00000000";
        long amount = req.getTotalAmount() != null ? req.getTotalAmount() : 850000L;

        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String pnrCode = generatePnrCode();

        // Acquire thread-safe seat lock
        String seatNumber = seatLockManager.acquireSeatLock(flight, date, bookingId);

        long paymentDue = Instant.now().getEpochSecond() + DEFAULT_LOCK_DURATION_SECONDS;

        BookingRecord record = new BookingRecord(
                bookingId,
                pnrCode,
                flight,
                date,
                name,
                passport,
                amount,
                BookingStatus.SEAT_LOCKED,
                paymentDue,
                seatNumber
        );

        try {
            repository.save(record);
        } catch (RuntimeException e) {
            seatLockManager.releaseSeatLock(bookingId);
            throw e;
        }
        bookings.put(bookingId, record);

        return new CreateBookingResponse(
                record.getBookingId(),
                record.getPnrCode(),
                record.getStatus().name(),
                record.getPaymentDueTimestamp()
        );
    }

    /**
     * GET /api/v1/bookings/{id}: Retrieves booking details.
     */
    public BookingDetailResponse getBooking(String bookingId) {
        BookingRecord record = findRecordOrThrow(bookingId);
        return new BookingDetailResponse(
                record.getBookingId(),
                record.getPnrCode(),
                record.getFlightNumber(),
                record.getDepartureDate(),
                record.getPassengerName(),
                record.getPassengerPassportNumber(),
                record.getStatus().name(),
                record.getTotalAmount(),
                record.getTicketNumber(),
                record.getSeatNumber(),
                record.getPaymentDueTimestamp(),
                record.getRefundAmount(),
                record.getPenaltyFee()
        );
    }

    /**
     * POST /api/v1/tickets/issue: Issues e-ticket and generates valid %PDF-1.4 binary.
     */
    public IssueTicketResponse issueTicket(IssueTicketRequest req) {
        if (req == null) {
            throw new ValidationException("Request body cannot be null");
        }
        if (req.getBookingId() == null || req.getBookingId().trim().isEmpty()) {
            throw new ValidationException("bookingId is required");
        }
        if (req.getPaymentTransactionId() == null || req.getPaymentTransactionId().trim().isEmpty()) {
            throw new ValidationException("paymentTransactionId is required");
        }

        String bookingId = req.getBookingId().trim();
        String txId = req.getPaymentTransactionId().trim();

        BookingRecord record = findRecordOrThrow(bookingId);

        synchronized (record) {
            // Validate FSM transitions
            if (record.getStatus() == BookingStatus.CANCELLED) {
                throw new InvalidTransitionException("Cannot issue ticket for cancelled booking (terminal state)");
            }
            if (record.getStatus() == BookingStatus.ISSUED) {
                throw new InvalidTransitionException("Ticket already issued for booking " + bookingId);
            }
            if (record.getStatus() != BookingStatus.SEAT_LOCKED && record.getStatus() != BookingStatus.PAID) {
                throw new InvalidTransitionException("Invalid booking status for issuance: " + record.getStatus());
            }

            // Generate official 724-XXXXXXXXXX ticket number
            String ticketNumber = generateTicketNumber();

            // Generate valid Base64-encoded %PDF-1.4 binary
            String base64Pdf = pdfTicketGenerator.generateBase64Pdf(
                    ticketNumber,
                    record.getPnrCode(),
                    record.getPassengerName(),
                    record.getPassengerPassportNumber(),
                    record.getFlightNumber(),
                    record.getDepartureDate(),
                    record.getSeatNumber()
            );

            record.setTicketNumber(ticketNumber);
            record.setPaymentTransactionId(txId);
            record.setStatus(BookingStatus.ISSUED);
            record.setIssuedAt(System.currentTimeMillis());
            repository.save(record);

            return new IssueTicketResponse(
                    record.getBookingId(),
                    record.getTicketNumber(),
                    record.getStatus().name(),
                    base64Pdf
            );
        }
    }

    /**
     * POST /api/v1/bookings/{id}/cancel: Cancels booking, releases seat lock, and calculates refund/penalty.
     */
    public CancelBookingResponse cancelBooking(String bookingId, String cancelReason) {
        BookingRecord record = findRecordOrThrow(bookingId);

        synchronized (record) {
            if (record.getStatus() == BookingStatus.CANCELLED) {
                throw new InvalidTransitionException("Booking is already cancelled (terminal state)");
            }

            long total = record.getTotalAmount();
            long penalty = 0;
            long refund = total;

            if (record.getStatus() == BookingStatus.ISSUED) {
                // 20% penalty fee after ticketing
                penalty = (total * 20) / 100;
                refund = total - penalty;
            } else if (record.getStatus() == BookingStatus.PAID) {
                // 10% penalty fee after payment but before issuance
                penalty = (total * 10) / 100;
                refund = total - penalty;
            } else {
                // 0% penalty fee during SEAT_LOCKED / PENDING
                penalty = 0;
                refund = total;
            }

            // Release seat lock
            seatLockManager.releaseSeatLock(record.getBookingId());

            record.setStatus(BookingStatus.CANCELLED);
            record.setCancelReason(cancelReason != null ? cancelReason : "Customer request");
            record.setPenaltyFee(penalty);
            record.setRefundAmount(refund);
            record.setCancelledAt(System.currentTimeMillis());
            repository.save(record);

            return new CancelBookingResponse(
                    record.getBookingId(),
                    record.getStatus().name(),
                    record.getRefundAmount(),
                    record.getPenaltyFee()
            );
        }
    }

    public BookingRecord getRecord(String bookingId) {
        return findRecordOrThrow(bookingId);
    }

    public Collection<BookingRecord> getAllBookings() {
        return Collections.unmodifiableCollection(bookings.values());
    }

    public SeatLockManager getSeatLockManager() {
        return seatLockManager;
    }

    public PdfTicketGenerator getPdfTicketGenerator() {
        return pdfTicketGenerator;
    }

    public void clear() {
        bookings.clear();
        repository.clear();
        seatLockManager.clearAll();
    }

    private BookingRecord findRecordOrThrow(String bookingId) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            throw new BookingNotFoundException("null");
        }
        String id = bookingId.trim();
        BookingRecord record = bookings.get(id);
        if (record == null) {
            // Not cached (e.g. created by another instance): load once and cache the shared instance.
            BookingRecord loaded = repository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
            BookingRecord prev = bookings.putIfAbsent(id, loaded);
            record = prev != null ? prev : loaded;
        }
        return record;
    }

    private String generatePnrCode() {
        StringBuilder sb = new StringBuilder("AC"); // AeroCore prefix
        for (int i = 0; i < 4; i++) {
            int idx = random.nextInt(PNR_CHARSET.length());
            sb.append(PNR_CHARSET.charAt(idx));
        }
        return sb.toString();
    }

    private String generateTicketNumber() {
        // Airline code 724-XXXXXXXXXX (10-digit sequential or random number)
        long number = 1000000000L + (long)(random.nextDouble() * 8999999999L);
        return "724-" + number;
    }
}
