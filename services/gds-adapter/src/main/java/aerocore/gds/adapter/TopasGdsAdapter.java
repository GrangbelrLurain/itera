package aerocore.gds.adapter;

import aerocore.gds.client.GdsClient;
import aerocore.gds.client.TopasClient;
import aerocore.gds.model.*;

import java.util.logging.Logger;

/**
 * Translates reservation commands into Topas-style host calls (simulated: no vendor SDK or real host is involved).
 */
public class TopasGdsAdapter implements GdsAdapter {
    private static final Logger log = Logger.getLogger(TopasGdsAdapter.class.getName());
    private final GdsClient topasClient;

    public TopasGdsAdapter() {
        this(new TopasClient());
    }

    public TopasGdsAdapter(GdsClient topasClient) {
        this.topasClient = topasClient;
    }

    @Override
    public GdsType getSupportedGds() {
        return GdsType.TOPAS;
    }

    @Override
    public GdsPnrResponse reserveSeat(GdsPnrRequest request) {
        log.info("[TOPAS Adapter] Converting Protobuf booking to TOPAS EDIFACT command: " + request);
        try {
            return topasClient.createPnr(request);
        } catch (Exception e) {
            log.severe("[TOPAS Adapter] Error reserving seat: " + e.getMessage());
            return new GdsPnrResponse("", GdsType.TOPAS, "", "ERR: " + e.getMessage(), false);
        }
    }

    @Override
    public GdsTicketResult finalizeTicketing(GdsTicketCommand command) {
        log.info("[TOPAS Adapter] Issuing e-ticket for PNR: " + command.getPnrCode());
        try {
            return topasClient.issueTicket(command);
        } catch (Exception e) {
            log.severe("[TOPAS Adapter] Ticketing failure: " + e.getMessage());
            return GdsTicketResult.failure(command.getPnrCode(), GdsType.TOPAS, e.getMessage());
        }
    }

    @Override
    public boolean voidReservation(String pnrCode) {
        log.info("[TOPAS Adapter] Cancelling PNR: " + pnrCode);
        try {
            return topasClient.cancelPnr(pnrCode);
        } catch (Exception e) {
            return false;
        }
    }
}
