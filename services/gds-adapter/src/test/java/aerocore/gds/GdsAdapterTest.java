package aerocore.gds;

import aerocore.gds.adapter.*;
import aerocore.gds.model.*;
import aerocore.gds.service.GdsAdapterService;
import aerocore.gds.service.GdsAdapterService.GdsBookingResult;

/**
 * Self-contained unit & integration test suite for the GDS adapter.
 * Can be executed via JUnit 5 or directly with Java CLI.
 */
public class GdsAdapterTest {

    public static void main(String[] args) {
        System.out.println("Running AeroCore GdsAdapterTest Suite...");
        GdsAdapterTest test = new GdsAdapterTest();

        int passed = 0;
        int failed = 0;

        try {
            test.testTopasAdapterPnrCreation();
            System.out.println("✔ testTopasAdapterPnrCreation PASSED");
            passed++;
        } catch (Throwable t) {
            System.err.println("✘ testTopasAdapterPnrCreation FAILED: " + t.getMessage());
            failed++;
        }

        try {
            test.testAmadeusAdapterPnrCreation();
            System.out.println("✔ testAmadeusAdapterPnrCreation PASSED");
            passed++;
        } catch (Throwable t) {
            System.err.println("✘ testAmadeusAdapterPnrCreation FAILED: " + t.getMessage());
            failed++;
        }

        try {
            test.testFactoryRoutingByFlightNumber();
            System.out.println("✔ testFactoryRoutingByFlightNumber PASSED");
            passed++;
        } catch (Throwable t) {
            System.err.println("✘ testFactoryRoutingByFlightNumber FAILED: " + t.getMessage());
            failed++;
        }

        try {
            test.testServiceEndToEndTicketingFlow();
            System.out.println("✔ testServiceEndToEndTicketingFlow PASSED");
            passed++;
        } catch (Throwable t) {
            System.err.println("✘ testServiceEndToEndTicketingFlow FAILED: " + t.getMessage());
            failed++;
        }

        try {
            test.testValidationAndCancellation();
            System.out.println("✔ testValidationAndCancellation PASSED");
            passed++;
        } catch (Throwable t) {
            System.err.println("✘ testValidationAndCancellation FAILED: " + t.getMessage());
            failed++;
        }

        System.out.printf("\nGdsAdapterTest Summary: %d passed, %d failed\n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    public void testTopasAdapterPnrCreation() {
        GdsAdapter adapter = new TopasGdsAdapter();
        GdsPnrRequest req = new GdsPnrRequest("KE701", "2026-11-20", "HONG GILDONG", "M12345678", "Y", 850000L);
        GdsPnrResponse resp = adapter.reserveSeat(req);

        if (!resp.isSuccess()) {
            throw new AssertionError("Expected TOPAS PNR reservation to succeed");
        }
        if (resp.getGdsType() != GdsType.TOPAS) {
            throw new AssertionError("Expected GdsType TOPAS, got " + resp.getGdsType());
        }
        if (!resp.getPnrCode().startsWith("TP")) {
            throw new AssertionError("Expected PNR to start with TP, got " + resp.getPnrCode());
        }
    }

    public void testAmadeusAdapterPnrCreation() {
        GdsAdapter adapter = new AmadeusGdsAdapter();
        GdsPnrRequest req = new GdsPnrRequest("OZ101", "2026-11-20", "CHOI MINSIK", "M87654321", "C", 1400000L);
        GdsPnrResponse resp = adapter.reserveSeat(req);

        if (!resp.isSuccess()) {
            throw new AssertionError("Expected Amadeus PNR reservation to succeed");
        }
        if (resp.getGdsType() != GdsType.AMADEUS) {
            throw new AssertionError("Expected GdsType AMADEUS, got " + resp.getGdsType());
        }
        if (!resp.getPnrCode().startsWith("1A")) {
            throw new AssertionError("Expected PNR to start with 1A, got " + resp.getPnrCode());
        }
    }

    public void testFactoryRoutingByFlightNumber() {
        GdsAdapterFactory factory = new GdsAdapterFactory();

        GdsAdapter keAdapter = factory.getAdapterForFlight("KE081");
        if (keAdapter.getSupportedGds() != GdsType.TOPAS) {
            throw new AssertionError("Expected KE to route to TOPAS");
        }

        GdsAdapter ozAdapter = factory.getAdapterForFlight("OZ202");
        if (ozAdapter.getSupportedGds() != GdsType.AMADEUS) {
            throw new AssertionError("Expected OZ to route to AMADEUS");
        }

        GdsAdapter lhAdapter = factory.getAdapterForFlight("LH718");
        if (lhAdapter.getSupportedGds() != GdsType.AMADEUS) {
            throw new AssertionError("Expected LH to route to AMADEUS");
        }
    }

    public void testServiceEndToEndTicketingFlow() {
        GdsAdapterService service = new GdsAdapterService();

        // 1. Create booking
        GdsBookingResult booking = service.createBooking("KE701", "2026-11-20", "PARK JISUNG", "M11223344", 800000L);
        if (!booking.isSuccess()) {
            throw new AssertionError("Create booking failed: " + booking.getErrorMessage());
        }

        // 2. Issue ticket
        GdsTicketResult ticket = service.issueTicket(booking.getPnrCode(), "KE701", "TX-2026-PAY", 800000L);
        if (!ticket.isSuccess()) {
            throw new AssertionError("Ticket issuance failed: " + ticket.getErrorMessage());
        }
        if (!ticket.getTicketNumber().startsWith("180-")) {
            throw new AssertionError("Expected ticket number to start with 180-, got " + ticket.getTicketNumber());
        }
    }

    public void testValidationAndCancellation() {
        GdsAdapterService service = new GdsAdapterService();

        // Empty passenger name should fail validation
        GdsBookingResult badBooking = service.createBooking("KE701", "2026-11-20", "", "M111", 500000L);
        if (badBooking.isSuccess()) {
            throw new AssertionError("Expected empty passenger name to fail");
        }

        // Cancellation should succeed
        boolean cancelled = service.cancelBooking("TP999X", "KE701");
        if (!cancelled) {
            throw new AssertionError("Expected cancellation to succeed");
        }
    }
}
