package aerocore.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class IssueTicketRequest {
    private String bookingId;
    private String paymentTransactionId;

    public IssueTicketRequest() {}

    public IssueTicketRequest(String bookingId, String paymentTransactionId) {
        this.bookingId = bookingId;
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getBookingId() { return bookingId; }
    @JsonAlias("booking_id")
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getPaymentTransactionId() { return paymentTransactionId; }
    @JsonAlias("payment_transaction_id")
    public void setPaymentTransactionId(String paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }
}
