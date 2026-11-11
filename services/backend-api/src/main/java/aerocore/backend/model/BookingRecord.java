package aerocore.backend.model;

/**
 * In-memory thread-safe domain entity representing a booking record in AeroCore.
 */
public class BookingRecord {
    private final String bookingId;
    private final String pnrCode;
    private final String flightNumber;
    private final String departureDate;
    private final String passengerName;
    private final String passengerPassportNumber;
    private final long totalAmount;
    private volatile BookingStatus status;
    private final long paymentDueTimestamp;
    private volatile String seatNumber;
    private volatile String ticketNumber;
    private volatile String paymentTransactionId;
    private volatile String cancelReason;
    private volatile long refundAmount;
    private volatile long penaltyFee;
    private final long createdAt;
    private volatile long issuedAt;
    private volatile long cancelledAt;

    public BookingRecord(String bookingId,
                         String pnrCode,
                         String flightNumber,
                         String departureDate,
                         String passengerName,
                         String passengerPassportNumber,
                         long totalAmount,
                         BookingStatus status,
                         long paymentDueTimestamp,
                         String seatNumber) {
        this(bookingId, pnrCode, flightNumber, departureDate, passengerName, passengerPassportNumber,
                totalAmount, status, paymentDueTimestamp, seatNumber, System.currentTimeMillis());
    }

    private BookingRecord(String bookingId,
                          String pnrCode,
                          String flightNumber,
                          String departureDate,
                          String passengerName,
                          String passengerPassportNumber,
                          long totalAmount,
                          BookingStatus status,
                          long paymentDueTimestamp,
                          String seatNumber,
                          long createdAt) {
        this.bookingId = bookingId;
        this.pnrCode = pnrCode;
        this.flightNumber = flightNumber;
        this.departureDate = departureDate;
        this.passengerName = passengerName;
        this.passengerPassportNumber = passengerPassportNumber;
        this.totalAmount = totalAmount;
        this.status = status;
        this.paymentDueTimestamp = paymentDueTimestamp;
        this.seatNumber = seatNumber;
        this.ticketNumber = "";
        this.paymentTransactionId = null;
        this.cancelReason = null;
        this.refundAmount = 0;
        this.penaltyFee = 0;
        this.createdAt = createdAt;
        this.issuedAt = 0;
        this.cancelledAt = 0;
    }

    /** Rehydrates a persisted booking (all fields, including timestamps) from storage. */
    public static BookingRecord restore(String bookingId, String pnrCode, String flightNumber, String departureDate,
                                        String passengerName, String passengerPassportNumber, long totalAmount,
                                        BookingStatus status, long paymentDueTimestamp, String seatNumber,
                                        String ticketNumber, String paymentTransactionId, String cancelReason,
                                        long refundAmount, long penaltyFee, long createdAt, long issuedAt,
                                        long cancelledAt) {
        BookingRecord r = new BookingRecord(bookingId, pnrCode, flightNumber, departureDate, passengerName,
                passengerPassportNumber, totalAmount, status, paymentDueTimestamp, seatNumber, createdAt);
        r.ticketNumber = ticketNumber;
        r.paymentTransactionId = paymentTransactionId;
        r.cancelReason = cancelReason;
        r.refundAmount = refundAmount;
        r.penaltyFee = penaltyFee;
        r.issuedAt = issuedAt;
        r.cancelledAt = cancelledAt;
        return r;
    }

    public String getBookingId() { return bookingId; }
    public String getPnrCode() { return pnrCode; }
    public String getFlightNumber() { return flightNumber; }
    public String getDepartureDate() { return departureDate; }
    public String getPassengerName() { return passengerName; }
    public String getPassengerPassportNumber() { return passengerPassportNumber; }
    public long getTotalAmount() { return totalAmount; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public long getPaymentDueTimestamp() { return paymentDueTimestamp; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public String getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(String paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public long getRefundAmount() { return refundAmount; }
    public void setRefundAmount(long refundAmount) { this.refundAmount = refundAmount; }
    public long getPenaltyFee() { return penaltyFee; }
    public void setPenaltyFee(long penaltyFee) { this.penaltyFee = penaltyFee; }
    public long getCreatedAt() { return createdAt; }
    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }
    public long getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(long cancelledAt) { this.cancelledAt = cancelledAt; }
}
