package aerocore.gds.model;

import java.time.Instant;

/**
 * Result returned after E-Ticket issuance from the GDS.
 */
public class GdsTicketResult {
    private final String ticketNumber;
    private final String pnrCode;
    private final GdsType gdsType;
    private final String couponStatus;
    private final Instant issuedAt;
    private final boolean success;
    private final String errorMessage;

    public GdsTicketResult(String ticketNumber, String pnrCode, GdsType gdsType, 
                           String couponStatus, boolean success, String errorMessage) {
        this.ticketNumber = ticketNumber;
        this.pnrCode = pnrCode;
        this.gdsType = gdsType;
        this.couponStatus = couponStatus;
        this.issuedAt = Instant.now();
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static GdsTicketResult success(String ticketNumber, String pnrCode, GdsType gdsType) {
        return new GdsTicketResult(ticketNumber, pnrCode, gdsType, "OPEN", true, null);
    }

    public static GdsTicketResult failure(String pnrCode, GdsType gdsType, String errorMessage) {
        return new GdsTicketResult(null, pnrCode, gdsType, "FAILED", false, errorMessage);
    }

    public String getTicketNumber() { return ticketNumber; }
    public String getPnrCode() { return pnrCode; }
    public GdsType getGdsType() { return gdsType; }
    public String getCouponStatus() { return couponStatus; }
    public Instant getIssuedAt() { return issuedAt; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        return "GdsTicketResult{" +
                "ticketNumber='" + ticketNumber + '\'' +
                ", pnrCode='" + pnrCode + '\'' +
                ", gdsType=" + gdsType +
                ", success=" + success +
                '}';
    }
}
