package aerocore.gds.model;

/**
 * Command payload to issue E-Ticket in the GDS.
 */
public class GdsTicketCommand {
    private final String pnrCode;
    private final String paymentTransactionId;
    private final long paidAmount;
    private final String issuingOfficeId;

    public GdsTicketCommand(String pnrCode, String paymentTransactionId, long paidAmount, String issuingOfficeId) {
        this.pnrCode = pnrCode;
        this.paymentTransactionId = paymentTransactionId;
        this.paidAmount = paidAmount;
        this.issuingOfficeId = issuingOfficeId != null ? issuingOfficeId : "SELKE08AA";
    }

    public String getPnrCode() { return pnrCode; }
    public String getPaymentTransactionId() { return paymentTransactionId; }
    public long getPaidAmount() { return paidAmount; }
    public String getIssuingOfficeId() { return issuingOfficeId; }
}
