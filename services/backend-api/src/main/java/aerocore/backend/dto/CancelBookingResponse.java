package aerocore.backend.dto;

public class CancelBookingResponse {
    private String bookingId;
    private String status;
    private long refundAmount;
    private long penaltyFee;

    public CancelBookingResponse() {}

    public CancelBookingResponse(String bookingId, String status, long refundAmount, long penaltyFee) {
        this.bookingId = bookingId;
        this.status = status;
        this.refundAmount = refundAmount;
        this.penaltyFee = penaltyFee;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getRefundAmount() { return refundAmount; }
    public void setRefundAmount(long refundAmount) { this.refundAmount = refundAmount; }

    public long getPenaltyFee() { return penaltyFee; }
    public void setPenaltyFee(long penaltyFee) { this.penaltyFee = penaltyFee; }
}
