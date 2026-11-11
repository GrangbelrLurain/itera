package aerocore.backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Built-in PDF-1.4 E-Ticket binary generator for AeroCore.
 * Generates standards-compliant, lightweight %PDF-1.4 electronic tickets with exact byte offsets.
 */
public class PdfTicketGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.of("Asia/Seoul"));

    /**
     * Generates a valid %PDF-1.4 binary byte array for an issued flight ticket.
     */
    public byte[] generateTicketPdfBytes(String ticketNumber,
                                        String pnrCode,
                                        String passengerName,
                                        String passportNumber,
                                        String flightNumber,
                                        String departureDate,
                                        String seatNumber) {
        String issueTime = DATE_FORMATTER.format(Instant.now());
        String safePassenger = sanitizePdfText(passengerName);
        String safePassport = sanitizePdfText(passportNumber != null ? passportNumber : "N/A");
        String safeFlight = sanitizePdfText(flightNumber);
        String safeDate = sanitizePdfText(departureDate);
        String safeSeat = sanitizePdfText(seatNumber != null ? seatNumber : "ASSIGNED_AT_GATE");
        String safeTicket = sanitizePdfText(ticketNumber);
        String safePnr = sanitizePdfText(pnrCode);

        // PDF text stream commands
        String streamContent = "BT\n" +
                "/F1 20 Tf\n" +
                "50 730 Td\n" +
                "(AeroCore Enterprise E-Ticket Receipt) Tj\n" +
                "/F1 12 Tf\n" +
                "0 -40 Td (----------------------------------------------------------------------) Tj\n" +
                "0 -25 Td (Ticket Number : " + safeTicket + ") Tj\n" +
                "0 -22 Td (PNR Code      : " + safePnr + ") Tj\n" +
                "0 -22 Td (Passenger Name: " + safePassenger + ") Tj\n" +
                "0 -22 Td (Passport No   : " + safePassport + ") Tj\n" +
                "0 -22 Td (Flight Number : " + safeFlight + ") Tj\n" +
                "0 -22 Td (Departure Date: " + safeDate + ") Tj\n" +
                "0 -22 Td (Assigned Seat : " + safeSeat + ") Tj\n" +
                "0 -22 Td (Booking Status: CONFIRMED / ISSUED) Tj\n" +
                "0 -22 Td (Issue Date    : " + issueTime + ") Tj\n" +
                "0 -30 Td (----------------------------------------------------------------------) Tj\n" +
                "0 -25 Td (Notice: Valid for travel upon presentation of matching government photo ID.) Tj\n" +
                "0 -18 Td (AeroCore Global Flight Reservation & Settlement Platform - Phase 1) Tj\n" +
                "ET\n";

        byte[] streamBytes = streamContent.getBytes(StandardCharsets.US_ASCII);

        StringBuilder sb = new StringBuilder();
        sb.append("%PDF-1.4\n");
        sb.append("%\u00e2\u00e3\u00cf\u00d3\n"); // Standard binary marker comment

        int offsetObj1 = sb.toString().getBytes(StandardCharsets.US_ASCII).length;
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        int offsetObj2 = sb.toString().getBytes(StandardCharsets.US_ASCII).length;
        sb.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        int offsetObj3 = sb.toString().getBytes(StandardCharsets.US_ASCII).length;
        sb.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n");

        int offsetObj4 = sb.toString().getBytes(StandardCharsets.US_ASCII).length;
        sb.append("4 0 obj\n<< /Length ").append(streamBytes.length).append(" >>\nstream\n");
        sb.append(streamContent);
        sb.append("endstream\nendobj\n");

        int offsetObj5 = sb.toString().getBytes(StandardCharsets.US_ASCII).length;
        sb.append("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

        int xrefOffset = sb.toString().getBytes(StandardCharsets.US_ASCII).length;
        sb.append("xref\n0 6\n");
        sb.append("0000000000 65535 f \n");
        sb.append(String.format("%010d 00000 n \n", offsetObj1));
        sb.append(String.format("%010d 00000 n \n", offsetObj2));
        sb.append(String.format("%010d 00000 n \n", offsetObj3));
        sb.append(String.format("%010d 00000 n \n", offsetObj4));
        sb.append(String.format("%010d 00000 n \n", offsetObj5));
        sb.append("trailer\n<< /Size 6 /Root 1 0 R >>\n");
        sb.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");

        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Generates Base64-encoded PDF binary.
     */
    public String generateBase64Pdf(String ticketNumber,
                                    String pnrCode,
                                    String passengerName,
                                    String passportNumber,
                                    String flightNumber,
                                    String departureDate,
                                    String seatNumber) {
        byte[] pdfBytes = generateTicketPdfBytes(
                ticketNumber, pnrCode, passengerName, passportNumber, flightNumber, departureDate, seatNumber);
        return Base64.getEncoder().encodeToString(pdfBytes);
    }

    private static String sanitizePdfText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("\r", " ")
                   .replace("\n", " ");
    }
}
