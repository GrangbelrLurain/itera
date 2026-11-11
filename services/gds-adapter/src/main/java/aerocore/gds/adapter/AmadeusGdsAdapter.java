package aerocore.gds.adapter;

import aerocore.gds.client.AmadeusClient;
import aerocore.gds.client.GdsClient;
import aerocore.gds.model.*;

import java.util.logging.Logger;

/**
 * AmadeusGdsAdapter translates modern AeroCore reservation commands to Amadeus 1A Web Services calls.
 */
public class AmadeusGdsAdapter implements GdsAdapter {
    private static final Logger log = Logger.getLogger(AmadeusGdsAdapter.class.getName());
    private final GdsClient amadeusClient;

    public AmadeusGdsAdapter() {
        this(new AmadeusClient());
    }

    public AmadeusGdsAdapter(GdsClient amadeusClient) {
        this.amadeusClient = amadeusClient;
    }

    @Override
    public GdsType getSupportedGds() {
        return GdsType.AMADEUS;
    }

    @Override
    public GdsPnrResponse reserveSeat(GdsPnrRequest request) {
        log.info("[Amadeus Adapter] Converting Protobuf booking to Amadeus XML WebService: " + request);
        try {
            return amadeusClient.createPnr(request);
        } catch (Exception e) {
            log.severe("[Amadeus Adapter] Error reserving seat: " + e.getMessage());
            return new GdsPnrResponse("", GdsType.AMADEUS, "", "ERR: " + e.getMessage(), false);
        }
    }

    @Override
    public GdsTicketResult finalizeTicketing(GdsTicketCommand command) {
        log.info("[Amadeus Adapter] Requesting TST e-ticket for PNR: " + command.getPnrCode());
        try {
            return amadeusClient.issueTicket(command);
        } catch (Exception e) {
            log.severe("[Amadeus Adapter] Ticketing failure: " + e.getMessage());
            return GdsTicketResult.failure(command.getPnrCode(), GdsType.AMADEUS, e.getMessage());
        }
    }

    @Override
    public boolean voidReservation(String pnrCode) {
        log.info("[Amadeus Adapter] Cancelling PNR: " + pnrCode);
        try {
            return amadeusClient.cancelPnr(pnrCode);
        } catch (Exception e) {
            return false;
        }
    }
}
