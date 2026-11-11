package aerocore.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test of the flight booking prototype over a real HTTP socket
 * (no database configured: in-memory storage, package and admin APIs disabled).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApiIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @LocalServerPort
    int port;

    private String baseUrl;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) throws IOException {
        return MAPPER.readValue(json, Map.class);
    }

    @Test
    @DisplayName("GET /api/v1/health returns 200 OK with UP status")
    void testHealthEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/health"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        Map<String, Object> json = parse(resp.body());
        assertEquals("UP", json.get("status"));
        assertEquals("1.0.0-java", json.get("version"));
    }

    @Test
    @DisplayName("E2E: Create Booking -> Get Details -> Issue Ticket -> Verify PDF -> Cancel Booking")
    void testFullBookingLifecycleEndToEnd() throws Exception {
        // 1. Create Booking
        String createJson = "{\"flightNumber\":\"KE701\",\"departureDate\":\"2026-11-20\"," +
                "\"passengerName\":\"HONG GILDONG\",\"passengerPassportNumber\":\"M12345678\",\"totalAmount\":850000}";

        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createJson))
                .build();

        HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResp.statusCode(), "Expected 201 Created for booking");

        Map<String, Object> created = parse(createResp.body());
        String bookingId = String.valueOf(created.get("bookingId"));
        String pnrCode = String.valueOf(created.get("pnrCode"));

        assertNotNull(bookingId);
        assertTrue(bookingId.startsWith("BK-"));
        assertNotNull(pnrCode);
        assertEquals(6, pnrCode.length());
        assertEquals("SEAT_LOCKED", created.get("status"));
        assertNotNull(created.get("paymentDueTimestamp"));

        // 2. Query Booking Detail
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings/" + bookingId))
                .GET()
                .build();

        HttpResponse<String> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResp.statusCode());

        Map<String, Object> detail = parse(getResp.body());
        assertEquals(bookingId, detail.get("bookingId"));
        assertEquals("KE701", detail.get("flightNumber"));
        assertEquals("HONG GILDONG", detail.get("passengerName"));
        assertEquals("SEAT_LOCKED", detail.get("status"));
        assertEquals("1A", detail.get("seatNumber"));

        // 3. Issue Ticket
        String issueJson = "{\"bookingId\":\"" + bookingId + "\",\"paymentTransactionId\":\"TX-PAY-2026-KE701\"}";
        HttpRequest issueReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/tickets/issue"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(issueJson))
                .build();

        HttpResponse<String> issueResp = httpClient.send(issueReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, issueResp.statusCode());

        Map<String, Object> issued = parse(issueResp.body());
        String ticketNo = String.valueOf(issued.get("ticketNumber"));
        assertNotNull(ticketNo);
        assertTrue(ticketNo.startsWith("724-"), "Ticket number must match 724-XXXXXXXXXX");
        assertEquals("ISSUED", issued.get("status"));

        // Verify Base64 PDF content
        String pdfBase64 = String.valueOf(issued.get("pdfDownloadUrl"));
        assertNotNull(pdfBase64);
        byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);
        String pdfHeader = new String(pdfBytes, 0, Math.min(20, pdfBytes.length), StandardCharsets.US_ASCII);
        assertTrue(pdfHeader.startsWith("%PDF-1.4"), "PDF must begin with valid %PDF-1.4 header");

        // 4. Download PDF Binary via REST
        HttpRequest pdfReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/tickets/" + ticketNo + "/pdf"))
                .GET()
                .build();

        HttpResponse<byte[]> pdfResp = httpClient.send(pdfReq, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pdfResp.statusCode());
        assertTrue(pdfResp.headers().firstValue("Content-Type").orElse("").contains("application/pdf"));
        assertTrue(new String(pdfResp.body(), StandardCharsets.US_ASCII).startsWith("%PDF-1.4"));

        // 5. Attempt duplicate ticketing -> 422 Unprocessable Entity
        HttpResponse<String> dupResp = httpClient.send(issueReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, dupResp.statusCode(), "Duplicate ticketing must return 422 Unprocessable Entity");

        // 6. Cancel Booking after ticketing (20% penalty fee)
        String cancelJson = "{\"cancelReason\":\"Passenger flight rescheduling\"}";
        HttpRequest cancelReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings/" + bookingId + "/cancel"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cancelJson))
                .build();

        HttpResponse<String> cancelResp = httpClient.send(cancelReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancelResp.statusCode());

        Map<String, Object> cancelled = parse(cancelResp.body());
        assertEquals("CANCELLED", cancelled.get("status"));
        assertEquals(170000L, ((Number) cancelled.get("penaltyFee")).longValue(), "20% penalty of 850,000 is 170,000");
        assertEquals(680000L, ((Number) cancelled.get("refundAmount")).longValue(), "Refund of 850,000 - 170,000 is 680,000");

        // 7. Attempt to issue ticket on cancelled booking -> 422 Unprocessable Entity
        HttpResponse<String> issueCancelledResp = httpClient.send(issueReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, issueCancelledResp.statusCode(), "Issuing ticket on cancelled booking must return 422");
    }

    @Test
    @DisplayName("Validation error handling: 400 Bad Request and 404 Not Found")
    void testValidationErrorsAnd404() throws Exception {
        // Missing passenger name -> 400 Bad Request
        String invalidJson = "{\"flightNumber\":\"KE701\",\"departureDate\":\"2026-11-20\",\"totalAmount\":500000}";
        HttpRequest badReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> badResp = httpClient.send(badReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, badResp.statusCode(), "Missing required fields must return 400 Bad Request");

        // Query non-existent booking -> 404 Not Found
        HttpRequest notFoundReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings/BK-DOES-NOT-EXIST"))
                .GET()
                .build();

        HttpResponse<String> notFoundResp = httpClient.send(notFoundReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, notFoundResp.statusCode(), "Query non-existent booking must return 404 Not Found");
    }

    @Test
    @DisplayName("Cancellation prior to ticketing: 0% penalty and 100% refund")
    void testCancellationPenaltyLogic() throws Exception {
        // Create booking (SEAT_LOCKED)
        String createJson = "{\"flightNumber\":\"OZ101\",\"departureDate\":\"2026-12-15\"," +
                "\"passengerName\":\"KIM MINJI\",\"passengerPassportNumber\":\"M99887766\",\"totalAmount\":600000}";

        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createJson))
                .build();

        HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> created = parse(createResp.body());
        String bookingId = String.valueOf(created.get("bookingId"));

        // Cancel while SEAT_LOCKED
        HttpRequest cancelReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings/" + bookingId + "/cancel"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"cancelReason\":\"Immediate refund\"}"))
                .build();

        HttpResponse<String> cancelResp = httpClient.send(cancelReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancelResp.statusCode());

        Map<String, Object> cancelled = parse(cancelResp.body());
        assertEquals(0L, ((Number) cancelled.get("penaltyFee")).longValue(), "Penalty must be 0 prior to ticketing");
        assertEquals(600000L, ((Number) cancelled.get("refundAmount")).longValue(), "Refund must be 100% prior to ticketing");
    }

    @Test
    @DisplayName("Without a database the package and admin APIs answer 503 UNAVAILABLE; unknown routes 404")
    void testUnconfiguredApisAndUnknownRoutes() throws Exception {
        for (String path : new String[] {"/api/v1/packages", "/api/v1/package-bookings/PB-1", "/api/v1/admin/dashboard"}) {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            // the admin route is guarded by the bearer-token interceptor, which needs the admin service too
            assertEquals(503, resp.statusCode(), path);
            assertEquals("UNAVAILABLE", parse(resp.body()).get("code"), path);
        }
        HttpResponse<String> notFound = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/nope")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, notFound.statusCode());
        assertEquals("NOT_FOUND", parse(notFound.body()).get("code"));
    }
}
