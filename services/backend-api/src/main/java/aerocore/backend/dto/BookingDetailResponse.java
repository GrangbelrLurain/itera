package aerocore.backend.dto;

public class BookingDetailResponse {
    private String bookingId;
    private String pnrCode;
    private String flightNumber;
    private String departureDate;
    private String passengerName;
    private String passengerPassportNumber;
    private String status;
    private long totalAmount;
    private String ticketNumber;
    private String seatNumber;
    private long paymentDueTimestamp;
    private long refundAmount;
    private long penaltyFee;

    public BookingDetailResponse() {}

    public BookingDetailResponse(String bookingId,
                                 String pnrCode,
                                 String flightNumber,
                                 String departureDate,
                                 String passengerName,
                                 String passengerPassportNumber,
                                 String status,
                                 long totalAmount,
                                 String ticketNumber,
                                 String seatNumber,
                                 long paymentDueTimestamp,
                                 long refundAmount,
                                 long penaltyFee) {
        this.bookingId = bookingId;
        this.pnrCode = pnrCode;
        this.flightNumber = flightNumber;
        this.departureDate = departureDate;
        this.passengerName = passengerName;
        this.passengerPassportNumber = passengerPassportNumber;
        this.status = status;
        this.totalAmount = totalAmount;
        this.ticketNumber = ticketNumber;
        this.seatNumber = seatNumber;
        this.paymentDueTimestamp = paymentDueTimestamp;
        this.refundAmount = refundAmount;
        this.penaltyFee = penaltyFee;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getPnrCode() { return pnrCode; }
    public void setPnrCode(String pnrCode) { this.pnrCode = pnrCode; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getDepartureDate() { return departureDate; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getPassengerPassportNumber() { return passengerPassportNumber; }
    public void setPassengerPassportNumber(String passengerPassportNumber) { this.passengerPassportNumber = passengerPassportNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(long totalAmount) { this.totalAmount = totalAmount; }

    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public long getPaymentDueTimestamp() { return paymentDueTimestamp; }
    public void setPaymentDueTimestamp(long paymentDueTimestamp) { this.paymentDueTimestamp = paymentDueTimestamp; }

    public long getRefundAmount() { return refundAmount; }
    public void setRefundAmount(long refundAmount) { this.refundAmount = refundAmount; }

    public long getPenaltyFee() { return penaltyFee; }
    public void setPenaltyFee(long penaltyFee) { this.penaltyFee = penaltyFee; }
}
