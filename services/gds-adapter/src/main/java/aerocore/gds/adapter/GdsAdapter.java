package aerocore.gds.adapter;

import aerocore.gds.model.*;

/**
 * Adapter interface conforming to AeroCore Hexagonal Port-Adapter Pattern.
 */
public interface GdsAdapter {
    GdsType getSupportedGds();
    GdsPnrResponse reserveSeat(GdsPnrRequest request);
    GdsTicketResult finalizeTicketing(GdsTicketCommand command);
    boolean voidReservation(String pnrCode);
}
