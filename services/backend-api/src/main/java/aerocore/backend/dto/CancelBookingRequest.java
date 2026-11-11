package aerocore.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CancelBookingRequest {
    private String cancelReason;

    public CancelBookingRequest() {}

    public CancelBookingRequest(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public String getCancelReason() { return cancelReason; }
    @JsonAlias("cancel_reason")
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
