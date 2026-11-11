package aerocore.backend;

import aerocore.backend.service.PdfTicketGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class PdfTicketGeneratorTest {

    private PdfTicketGenerator generator;

    @BeforeEach
    public void setUp() {
        generator = new PdfTicketGenerator();
    }

    public static void main(String[] args) {
        PdfTicketGeneratorTest test = new PdfTicketGeneratorTest();
        test.setUp();
        test.testValidPdf14BinaryStructure();
        test.setUp();
        test.testBase64PdfEncodingAndDecoding();
        System.out.println("✔ PdfTicketGeneratorTest all assertions passed!");
    }

    @Test
    @DisplayName("Verify generated PDF byte array conforms to %PDF-1.4 standard")
    public void testValidPdf14BinaryStructure() {
        byte[] pdfBytes = generator.generateTicketPdfBytes(
                "724-1234567890",
                "AC789X",
                "HONG GILDONG",
                "M12345678",
                "KE701",
                "2026-11-20",
                "12A"
        );

        assertNotNull(pdfBytes, "PDF bytes must not be null");
        assertTrue(pdfBytes.length > 300, "PDF byte length must be greater than 300 bytes");

        String pdfString = new String(pdfBytes, StandardCharsets.US_ASCII);

        // Standard PDF-1.4 header
        assertTrue(pdfString.startsWith("%PDF-1.4\n"), "PDF must begin with %PDF-1.4");

        // Required PDF structural elements
        assertTrue(pdfString.contains("<< /Type /Catalog /Pages 2 0 R >>"), "Catalog dictionary missing");
        assertTrue(pdfString.contains("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"), "Pages dictionary missing");
        assertTrue(pdfString.contains("/Type /Page"), "Page object missing");
        assertTrue(pdfString.contains("/Type /Font /Subtype /Type1 /BaseFont /Helvetica"), "Font dictionary missing");
        assertTrue(pdfString.contains("xref"), "Cross-reference table missing");
        assertTrue(pdfString.contains("trailer"), "Trailer dictionary missing");
        assertTrue(pdfString.contains("startxref"), "Startxref missing");
        assertTrue(pdfString.endsWith("%%EOF\n"), "PDF must terminate with %%EOF");

        // Domain content verification
        assertTrue(pdfString.contains("724-1234567890"), "Ticket number must be present in stream");
        assertTrue(pdfString.contains("AC789X"), "PNR code must be present in stream");
        assertTrue(pdfString.contains("HONG GILDONG"), "Passenger name must be present in stream");
        assertTrue(pdfString.contains("KE701"), "Flight number must be present in stream");
        assertTrue(pdfString.contains("CONFIRMED / ISSUED"), "Status must be present in stream");
    }

    @Test
    @DisplayName("Verify Base64 encoded PDF string decodes to valid %PDF-1.4")
    public void testBase64PdfEncodingAndDecoding() {
        String base64 = generator.generateBase64Pdf(
                "724-9876543210",
                "AC101Z",
                "KIM CHULSOO",
                "M87654321",
                "OZ101",
                "2026-11-25",
                "3B"
        );

        assertNotNull(base64, "Base64 PDF string must not be null");
        assertFalse(base64.isBlank(), "Base64 PDF string must not be blank");

        byte[] decoded = Base64.getDecoder().decode(base64);
        assertNotNull(decoded, "Decoded bytes must not be null");

        String decodedString = new String(decoded, StandardCharsets.US_ASCII);
        assertTrue(decodedString.startsWith("%PDF-1.4\n"), "Decoded PDF must start with %PDF-1.4");
        assertTrue(decodedString.endsWith("%%EOF\n"), "Decoded PDF must end with %%EOF");
        assertTrue(decodedString.contains("724-9876543210"), "Decoded PDF must contain ticket number");
    }
}
