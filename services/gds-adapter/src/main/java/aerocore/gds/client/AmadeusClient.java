package aerocore.gds.client;

import aerocore.gds.model.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Amadeus 1A Web Services GDS Client implementation.
 * Simulates Amadeus PNR_AddMultiElements & Ticket_CreateTSTFromPricing flows.
 */
public class AmadeusClient implements GdsClient {
    private static final AtomicLong TICKET_SEQ = new AtomicLong(9881002000L);

    @Override
    public GdsType getGdsType() {
        return GdsType.AMADEUS;
    }

    @Override
    public GdsPnrResponse createPnr(GdsPnrRequest request) {
        String rawSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String pnr = "1A" + rawSuffix;
        String airlineRef = "1A-" + request.getFlightNumber() + "-" + pnr;

        String rawTelegram = String.format(
            "<PNRReply><pnrHeader><reservationControl><controlNumber>%s</controlNumber></reservationControl></pnrHeader></PNRReply>",
            pnr
        );

        return new GdsPnrResponse(pnr, GdsType.AMADEUS, airlineRef, rawTelegram, true);
    }

    @Override
    public GdsTicketResult issueTicket(GdsTicketCommand command) {
        long seq = TICKET_SEQ.incrementAndGet();
        // Asiana airline code prefix: 988
        String ticketNumber = "988-" + seq;
        return GdsTicketResult.success(ticketNumber, command.getPnrCode(), GdsType.AMADEUS);
    }

    @Override
    public boolean cancelPnr(String pnrCode) {
        return pnrCode != null && !pnrCode.isBlank();
    }
}
