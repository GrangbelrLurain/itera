package aerocore.gds;

import aerocore.gds.service.GdsAdapterService;
import aerocore.gds.service.GdsAdapterService.GdsBookingResult;
import aerocore.gds.model.GdsTicketResult;

public class Main {
    public static void main(String[] args) {
        System.out.println("=====================================================");
        System.out.println("  Itera GDS Adapter (simulated, Java 17+)  ");
        System.out.println("  Connected Providers: TOPAS (KE), Amadeus (OZ, LH)  ");
        System.out.println("=====================================================");

        GdsAdapterService service = new GdsAdapterService();

        // 1. Simulate Korean Air booking via TOPAS adapter
        System.out.println("\n[Demo 1] Creating Booking on Korean Air (KE701) -> Routing to TOPAS...");
        GdsBookingResult keResult = service.createBooking(
                "KE701", "2026-11-20", "HONG GILDONG", "M12345678", 850000L
        );
        System.out.println("Result: " + keResult);

        if (keResult.isSuccess()) {
            System.out.println("\n[Demo 2] Issuing E-Ticket for TOPAS PNR: " + keResult.getPnrCode());
            GdsTicketResult ticketResult = service.issueTicket(
                    keResult.getPnrCode(), "KE701", "TX-PAY-001", 850000L
            );
            System.out.println("Ticket Result: " + ticketResult);
        }

        // 2. Simulate Asiana booking via Amadeus adapter
        System.out.println("\n[Demo 3] Creating Booking on Asiana Airlines (OZ101) -> Routing to Amadeus...");
        GdsBookingResult ozResult = service.createBooking(
                "OZ101", "2026-11-22", "KIM CHULSOO", "M87654321", 920000L
        );
        System.out.println("Result: " + ozResult);

        if (ozResult.isSuccess()) {
            System.out.println("\n[Demo 4] Issuing E-Ticket for Amadeus PNR: " + ozResult.getPnrCode());
            GdsTicketResult ticketResult = service.issueTicket(
                    ozResult.getPnrCode(), "OZ101", "TX-PAY-002", 920000L
            );
            System.out.println("Ticket Result: " + ticketResult);
        }

        System.out.println("\n[AeroCore GDS Adapter] All demo transactions completed successfully.");
    }
}
