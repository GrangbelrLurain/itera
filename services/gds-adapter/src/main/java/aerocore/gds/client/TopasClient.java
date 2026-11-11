package aerocore.gds.client;

import aerocore.gds.model.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TOPAS (Korean Air Sabre SDK) Client implementation.
 * Simulates a synchronous host socket / EDIFACT session (no real host).
 */
public class TopasClient implements GdsClient {
    private static final AtomicLong TICKET_SEQ = new AtomicLong(1809001000L);

    @Override
    public GdsType getGdsType() {
        return GdsType.TOPAS;
    }

    @Override
    public GdsPnrResponse createPnr(GdsPnrRequest request) {
        // Generate simulated 6-character TOPAS PNR
        String rawSuffix = UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        String pnr = "TP" + rawSuffix + "X";
        String airlineRef = "KE-" + request.getFlightNumber() + "-" + pnr;
        
        // Simulated EDIFACT Host response telegram
        String rawTelegram = String.format(
            "TOPAS/1A-HOST-ACK: PNR=%s FLIGHT=%s DATE=%s PAX=%s STATUS=HK1",
            pnr, request.getFlightNumber(), request.getDepartureDate(), request.getPassengerName()
        );

        return new GdsPnrResponse(pnr, GdsType.TOPAS, airlineRef, rawTelegram, true);
    }

    @Override
    public GdsTicketResult issueTicket(GdsTicketCommand command) {
        long seq = TICKET_SEQ.incrementAndGet();
        // Korean Air airline code prefix: 180
        String ticketNumber = "180-" + seq;
        return GdsTicketResult.success(ticketNumber, command.getPnrCode(), GdsType.TOPAS);
    }

    @Override
    public boolean cancelPnr(String pnrCode) {
        return pnrCode != null && !pnrCode.isBlank();
    }
}
