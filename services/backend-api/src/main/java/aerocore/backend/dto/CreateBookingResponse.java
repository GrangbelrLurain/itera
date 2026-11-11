package aerocore.backend.dto;

public class CreateBookingResponse {
    private String bookingId;
    private String pnrCode;
    private String status;
    private long paymentDueTimestamp;

    public CreateBookingResponse() {}

    public CreateBookingResponse(String bookingId, String pnrCode, String status, long paymentDueTimestamp) {
        this.bookingId = bookingId;
        this.pnrCode = pnrCode;
        this.status = status;
        this.paymentDueTimestamp = paymentDueTimestamp;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getPnrCode() { return pnrCode; }
    public void setPnrCode(String pnrCode) { this.pnrCode = pnrCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getPaymentDueTimestamp() { return paymentDueTimestamp; }
    public void setPaymentDueTimestamp(long paymentDueTimestamp) { this.paymentDueTimestamp = paymentDueTimestamp; }
}
