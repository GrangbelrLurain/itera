package aerocore.backend.dto;

public class IssueTicketResponse {
    private String bookingId;
    private String ticketNumber;
    private String status;
    private String pdfDownloadUrl;
    private String base64Data;

    public IssueTicketResponse() {}

    public IssueTicketResponse(String bookingId, String ticketNumber, String status, String pdfDownloadUrl) {
        this.bookingId = bookingId;
        this.ticketNumber = ticketNumber;
        this.status = status;
        this.pdfDownloadUrl = pdfDownloadUrl;
        this.base64Data = pdfDownloadUrl;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPdfDownloadUrl() { return pdfDownloadUrl; }
    public void setPdfDownloadUrl(String pdfDownloadUrl) {
        this.pdfDownloadUrl = pdfDownloadUrl;
        this.base64Data = pdfDownloadUrl;
    }

    public String getBase64Data() { return base64Data; }
    public void setBase64Data(String base64Data) { this.base64Data = base64Data; }
}
